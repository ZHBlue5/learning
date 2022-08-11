package com.example.demo.utils;

import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson.JSON;
import com.example.demo.entity.Bicycling;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.http.HttpRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * @author ZHBlue
 * @since 2022/8/9 09:38
 */
@Slf4j
public class CreateFenceUtil {

    /**
     * 圆周率
     */
    private static final double PI = 3.14159265;

    private static final String KEY = "高德web key";

    /**
     * 最大骑行距离
     */
    private static final Double MAX_DISTANCE = 3000D;

    private static final ThreadFactory DELIVERY_THREAD_FACTORY =
        new ThreadFactoryBuilder().setNamePrefix("门店骑行范围生成-").build();

    private static final ExecutorService POOL_EXECUTOR =
        new ThreadPoolExecutor(8, 30, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue(1024), DELIVERY_THREAD_FACTORY);

    public static void main(String[] args) throws IOException {
        LngLat shop = new LngLat();
        String str = "121.564075,31.212292";
        String[] split = str.split(",");
        shop.setLat(Double.parseDouble(split[1]));
        shop.setLng(Double.parseDouble(split[0]));
        shop.setNum(0);
        shop.setMin(0D);
        shop.setMax(MAX_DISTANCE);

        long startTime = System.currentTimeMillis();

        List<LngLat> lngLatList = cal(MAX_DISTANCE, shop);

        BufferedWriter write = getFileWrite("/Users/heytea/project/demo/yuanshi.txt");

        for (LngLat lat : lngLatList) {
            write.write(String.format("%s,%s\r\n", lat.getLng(), lat.getLat()));
        }

        closeFile(write);

        List<LngLat> boundarys = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<Point2D.Double> coordinatePoints = new ArrayList<>();
        for (LngLat row : lngLatList) {
            coordinatePoints.add(new Point2D.Double(row.getLat(), row.getLng()));
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                LngLat lat = new LngLat();
                lat.setLat(shop.getLat());
                lat.setLng(shop.getLng());
                lat.setMin(shop.getMin());
                lat.setMax(shop.getMax());
                lat.setDistance(shop.getDistance());
                lat.setNum(0);
                lat.setAngle(row.getAngle());
                lat.setLats(new ArrayList<>());
                lat.setI(row.getI());
                Integer distance = getRideDistance(lat, row);
                LngLat lat1 = judge(distance, lat, row);
                boundarys.add(lat1);
            }, POOL_EXECUTOR);
            futures.add(future);
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        log.info("获取各个点之间的骑行路线");
        List<LngLat> lats = boundarys.stream().sorted(Comparator.comparing(LngLat::getI)).collect(Collectors.toList());
        List<CompletableFuture<Void>> futureList = new ArrayList<>();
        int max = 0;
        int min = lats.get(0).getNum();
        int sum = 0;
        for (int i = 0; i < lats.size(); i++) {
            int finalI = i;
            LngLat lngLat = lats.get(i);
            if (lngLat.getNum() > max) {
                max = lngLat.getNum();
            }
            if (lngLat.getNum() < min) {
                min = lngLat.getNum();
            }
            sum += lngLat.getNum();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                List<Trajectory> trajectory;
                if (finalI == lats.size() - 1) {
                    trajectory = getRideTrajectory(lats.get(finalI), lats.get(0));
                } else {
                    trajectory = getRideTrajectory(lats.get(finalI), lats.get(finalI + 1));
                }
                lats.get(finalI).setTrajectory(trajectory);
            }, POOL_EXECUTOR);
            futureList.add(future);
        }

        try {
            CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        log.info("输出结果");
        BufferedWriter trajectoryWriter = getFileWrite("/Users/heytea/project/demo/qixing_guiji.txt");

        BufferedWriter qixing = getFileWrite("/Users/heytea/project/demo/qixing.txt");

        List<Trajectory> lngLats = new ArrayList<>();
        for (LngLat boundary : lats) {
            qixing.write(String.format("%s,%s\r\n", boundary.getLng(), boundary.getLat()));
            if (CollectionUtils.isEmpty(boundary.getTrajectory())) {
                // coordinatePoints.add(new Point2D.Double(boundary.getLat(), boundary.getLng()));
                trajectoryWriter.write(String.format("%s,%s\r\n", boundary.getLng(), boundary.getLat()));
            } else {
                trajectoryWriter.write(String.format("%s,%s\r\n", boundary.getLng(), boundary.getLat()));
                for (Trajectory lat : boundary.getTrajectory()) {
                    Point2D.Double point = new Point2D.Double(lat.getLat(), lat.getLng());
                    if (isInPolygon(point, coordinatePoints)) {
                        continue;
                    }
                    coordinatePoints.add(point);
                    trajectoryWriter.write(String.format("%s,%s\r\n", lat.getLng(), lat.getLat()));
                }
            }
        }

        log.info("[结果输出完成]");
        closeFile(trajectoryWriter);
        closeFile(qixing);

        log.info("关闭文件流");

        log.info("[统计]最大次数：{},最小次数:{},共请求「{}」次", max, min, sum);
        long endTime = System.currentTimeMillis();
        log.info("[执行时间]{}", endTime - startTime);
        return;
    }

    private static LngLat judge(Integer distance, LngLat lat, LngLat row) {
        lat.setNum(lat.getNum() + 1);
        LngLat lat1 = halve(distance, lat);
        if (Objects.isNull(lat1)) {
            return getValidLngLat(lat, row);

        }

        Integer distance1 = getRideDistance(lat, lat1);
        if (Objects.isNull(distance1)) {
            return getValidLngLat(lat, row);
        }
        lat1.setDistance(distance1 * 1.0);
        log.debug("第{}个,原先经纬度「{},{}」；新经纬度：「{},{}」;目前新骑行距离:{},获取骑行执行次数:{}", row.getI(), row.getLng(), row.getLat(),
            lat1.getLng(), lat1.getLat(), distance1, lat.getNum());
        lat.getLats().add(lat1);

        if (distance1 > MAX_DISTANCE || distance1 < (MAX_DISTANCE - 10)) {
            return judge(distance1, lat, row);
        }

        return lat1;
    }

    private static List<Trajectory> getRideTrajectory(LngLat lat1, LngLat lat2) {
        HashMap<String, Object> map = new HashMap<>(10);
        map.put("origin", String.format("%s,%s", lat1.getLng(), lat1.getLat()));
        map.put("destination", String.format("%s,%s", lat2.getLng(), lat2.getLat()));
        map.put("key", KEY);
        String body = HttpRequest.get("https://restapi.amap.com/v4/direction/bicycling").form(map).execute().body();

        Bicycling bicycling = JSON.parseObject(body, Bicycling.class);
        List<Bicycling.DataDTO.PathsDTO> paths = bicycling.getData().getPaths();
        List<Trajectory> lats = new ArrayList<>();
        if (CollectionUtils.isEmpty(paths)) {
            return lats;
        }
        log.info("[获取骑行轨迹]「{}」～「{}」", lat1.getI(), lat2.getI());
        Bicycling.DataDTO.PathsDTO dto =
            paths.stream().min(Comparator.comparing(Bicycling.DataDTO.PathsDTO::getDuration)).orElse(paths.get(0));
        for (Bicycling.DataDTO.PathsDTO.StepsDTO step : dto.getSteps()) {
            if (StringUtils.isEmpty(step.getPolyline())) {
                continue;
            }
            List<String> list = Arrays.asList(step.getPolyline().split(";"));
            for (String s : list) {
                String[] split = s.split(",");
                Trajectory trajectory = new Trajectory();
                trajectory.setLng(Double.parseDouble(split[0]));
                trajectory.setLat(Double.parseDouble(split[1]));
                trajectory.setDistance(step.getDistance() * 1.0);
                if (lats.contains(trajectory)) {
                    continue;
                }
                lats.add(trajectory);
            }
        }
        return lats;
    }

    /**
     * 获取有效的栅栏范围点
     * 
     * @param lat
     * @param row
     * @return
     */
    private static LngLat getValidLngLat(LngLat lat, LngLat row) {
        if (CollectionUtils.isEmpty(lat.getLats())) {
            return row;
        }
        Optional<LngLat> max = lat.getLats().stream().filter(x -> x.getDistance() < MAX_DISTANCE)
            .max(Comparator.comparing(LngLat::getLineDistance));

        if (max.isPresent()) {
            return max.get();
        }
        Optional<LngLat> min = lat.getLats().stream().min(Comparator.comparing(LngLat::getDistance));
        return min.orElse(row);
    }

    private static Integer getRideDistance(LngLat lat1, LngLat lat2) {
        HashMap<String, Object> map = new HashMap<>(10);
        map.put("origin", String.format("%s,%s", lat1.getLng(), lat1.getLat()));
        map.put("destination", String.format("%s,%s", lat2.getLng(), lat2.getLat()));
        map.put("key", KEY);
        String body = HttpRequest.get("https://restapi.amap.com/v4/direction/bicycling").form(map).execute().body();

        Bicycling bicycling = JSON.parseObject(body, Bicycling.class);
        if (!Objects.equals(bicycling.getErrcode(), 0)) {
            // 30001 无规划结果
            // 30006 您输入的起点信息有误,请检验是否符合接口使用规范
            List<Integer> code = Arrays.asList(30001, 30006, 30007);
            // 不合法
            if (code.contains(bicycling.getErrcode())) {
                log.error("[无规划结果]map:{}", map);
                return null;
            }
            throw new RuntimeException(bicycling.getErrmsg() + ";code:" + bicycling.getErrcode() + ";msg:"
                + bicycling.getErrdetail() + ";param:" + JSON.toJSONString(map));
        }
        return bicycling.getData().getPaths().get(0).getDistance();
    }

    private static LngLat halve(Integer distance, LngLat row) {
        double r;

        // 最大与最小的差小于10,不再计算
        if ((row.getMax() - row.getMin()) < 10) {
            return null;
        }

        double radian;
        // 若瞄点为空，则计算瞄点是否超
        if (Objects.isNull(row.getPoint())) {
            radian = (row.getMax() - row.getMin()) / 2 + row.getMin();
            row.setPoint(radian);
        } else {
            if (distance > MAX_DISTANCE) {
                row.setMax(row.getPoint());
            } else {
                row.setMin(row.getPoint());
            }
            row.setPoint(null);
            radian = (row.getMax() - row.getMin()) / 2 + row.getMin();
        }

        log.debug("第{}个;重新计算相差距离：max:「{}」,min:「{}」{};原先骑行距离:{},第「{}」次", row.getI(), row.getMax(), row.getMin(), radian,
            distance, row.getNum());
        r = radian / (2 * Math.PI * 6371004) * 360;
        double single = row.getAngle();
        double lng2 = row.getLng() + r * Math.cos(single);
        double lat2 = row.getLat() + r * Math.sin(single);
        LngLat lat1 = new LngLat();
        lat1.setLat(lat2);
        lat1.setLng(lng2);
        lat1.setI(row.getI());
        lat1.setAngle(row.getAngle());
        lat1.setMax(row.getMax());
        lat1.setMin(row.getMin());
        lat1.setNum(row.getNum());
        lat1.setPoint(row.getPoint());

        double line = distance(lat1.getLat(), lat1.getLng(), row.getLat(), row.getLng());
        lat1.setLineDistance(line);
        return lat1;
    }

    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        if ((Objects.equals(lat1, lat2)) && (Objects.equals(lon1, lon2))) {
            return 0;
        } else {
            double theta = lon1 - lon2;
            double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
            dist = Math.acos(dist);
            dist = Math.toDegrees(dist);
            dist = dist * 60 * 1.1515;
            dist = dist * 1609.344;
            return (dist);
        }
    }

    private static List<LngLat> cal(double t, LngLat lat) {
        double r = t / (2 * Math.PI * 6371004) * 360;

        List<LngLat> lngLats = new ArrayList<>(200);
        int first = 0;
        for (double i = 0; i <= 360; i = i + 1.8) {
            first++;
            double angle = i * Math.PI / 180;
            double lng = lat.getLng() + r * Math.cos(angle);
            double lat1 = lat.getLat() + r * Math.sin(angle);
            LngLat lat2 = new LngLat();
            lat2.setNum(0);
            lat2.setLat(lat1);
            lat2.setLng(lng);
            lat2.setI(first);
            lat2.setAngle(angle);
            lat2.setMin(0D);
            lat2.setMax(MAX_DISTANCE);

            lngLats.add(lat2);
        }

        return lngLats;

    }

    private static void closeFile(BufferedWriter writer) throws IOException {
        writer.flush();
        writer.close();
    }

    private static BufferedWriter getFileWrite(String path) throws IOException {
        File file = new File(path);
        file.createNewFile(); // 创建新文件
        return new BufferedWriter(new FileWriter(file));
    }

    @Data
    public static class LngLat {
        private Double lat;
        private Double lng;

        private Integer i;

        private Double angle;

        // 计算次数
        private Integer num;

        private List<LngLat> lats;

        private List<Trajectory> trajectory;

        private Double distance;

        // 最小距离
        private Double min;

        // 最大距离
        private Double max;

        private Double point;

        private Double lineDistance;
    }

    @Data
    public static class Trajectory {
        private Double lat;
        private Double lng;
        private Double distance;
    }

    /**
     * 判断点是否在多边形内 @Title: IsPointInPoly @param point 测试点 @param pts 多边形的点 @return @return boolean @throws
     */
    public static boolean isInPolygon(Point2D.Double point, List<Point2D.Double> pts) {

        int N = pts.size();
        boolean boundOrVertex = true;
        int intersectCount = 0;// 交叉点数量
        double precision = 2e-10; // 浮点类型计算时候与0比较时候的容差
        Point2D.Double p1, p2;// 临近顶点
        Point2D.Double p = point; // 当前点

        p1 = pts.get(0);
        for (int i = 1; i <= N; ++i) {
            if (p.equals(p1)) {
                return boundOrVertex;
            }

            p2 = pts.get(i % N);
            if (p.x < Math.min(p1.x, p2.x) || p.x > Math.max(p1.x, p2.x)) {
                p1 = p2;
                continue;
            }

            // 射线穿过算法
            if (p.x > Math.min(p1.x, p2.x) && p.x < Math.max(p1.x, p2.x)) {
                if (p.y <= Math.max(p1.y, p2.y)) {
                    if (p1.x == p2.x && p.y >= Math.min(p1.y, p2.y)) {
                        return boundOrVertex;
                    }

                    if (p1.y == p2.y) {
                        if (p1.y == p.y) {
                            return boundOrVertex;
                        } else {
                            ++intersectCount;
                        }
                    } else {
                        double xinters = (p.x - p1.x) * (p2.y - p1.y) / (p2.x - p1.x) + p1.y;
                        if (Math.abs(p.y - xinters) < precision) {
                            return boundOrVertex;
                        }

                        if (p.y < xinters) {
                            ++intersectCount;
                        }
                    }
                }
            } else {
                if (p.x == p2.x && p.y <= p2.y) {
                    Point2D.Double p3 = pts.get((i + 1) % N);
                    if (p.x >= Math.min(p1.x, p3.x) && p.x <= Math.max(p1.x, p3.x)) {
                        ++intersectCount;
                    } else {
                        intersectCount += 2;
                    }
                }
            }
            p1 = p2;
        }
        if (intersectCount % 2 == 0) {// 偶数在多边形外
            return false;
        } else { // 奇数在多边形内
            return true;
        }
    }
}
