### MYSQL
    使用外部mysql


### k8s mysql配置
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: nacos-cm
data:
  mysql.host: "10.127.1.12"
  mysql.db.name: "nacos_devtest"
  mysql.port: "3306"
  mysql.user: "nacos"
  mysql.password: "passwd"
```


#### k8s服务发现(service discovery)
```yaml
apiVersion: v1
kind: Service
metadata:
  name: nacos-k8s
  labels:
    app: nacos-k8s
spec:
  type: ClusterIP
  clusterIP: None
  ports:
    - port: 8848
      name: server
      targetPort: 8848
    - port: 9848
      name: client-rpc
      targetPort: 9848
    - port: 9849
      name: raft-rpc
      targetPort: 9849
      ## 兼容1.4.x版本的选举端口
    - port: 7848
      name: old-raft-rpc
      targetPort: 7848
  selector:
    app: nacos
```

#### nacos集群
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: nacos-k8s
spec:
  serviceName: nacos-headless
  replicas: 3
  template:
    metadata:
      labels:
        app: nacos
      annotations:
        pod.alpha.kubernetes.io/initialized: "true"
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchExpressions:
                  - key: "app"
                    operator: In
                    values:
                      - nacos-k8s
              topologyKey: "kubernetes.io/hostname"
      containers:
        - name: k8snacos
          imagePullPolicy: Always
          image: ccr.ccs.tencentyun.com/open-css/nacos:v2.1.0
          ports:
            - containerPort: 8848
              name: client
              hostPort: 30100
            - containerPort: 9848
              name: client-rpc
              hostPort: 31100
            - containerPort: 9849
              name: raft-rpc
              hostPort: 31101
            - containerPort: 7848
              name: old-raft-rpc
          env:
            - name: NACOS_REPLICAS
              value: "3"
            - name: MYSQL_SERVICE_HOST
              value: "172.31.151.6"
            - name: MYSQL_SERVICE_DB_NAME
              value: "nacos"
            - name: MYSQL_SERVICE_PORT
              value: "3306"
            - name: MYSQL_SERVICE_USER
              value: "nacos"
            - name: MYSQL_SERVICE_PASSWORD
              value: "2B8iFM5h97oubRVd"
            - name: MODE
              value: "cluster"
            - name: NACOS_SERVER_PORT
              value: "8848"
            - name: PREFER_HOST_MODE
              value: "hostname"
            - name: NACOS_SERVERS
              value: "nacos-k8s-0.nacos-headless.tencent.svc.cluster.local:8848 nacos-k8s-1.nacos-headless.tencent.svc.cluster.local:8848 nacos-k8s-2.nacos-headless.tencent.svc.cluster.local:8848"
      hostAliases:
      - hostnames:
        - mysql.css.dgut.edu.cn
        ip: 172.31.151.6
      imagePullSecrets:
      - name: tencent
  selector:
    matchLabels:
      app: nacos
```


#### 官方部署yaml
```yaml
##使用自建数据库；使用Ingress发布配置后台###
---
apiVersion: v1
kind: Service
metadata:
  name: nacos-headless
  labels:
    app: nacos-headless
spec:
  type: ClusterIP
  clusterIP: None
  ports:
    - port: 8848
      name: server
      targetPort: 8848
    - port: 9848
      name: client-rpc
      targetPort: 9848
    - port: 9849
      name: raft-rpc
      targetPort: 9849
      ## 兼容1.4.x版本的选举端口
    - port: 7848
      name: old-raft-rpc
      targetPort: 7848
  selector:
    app: nacos
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: nacos-cm
data:
  mysql.host: "10.127.1.12"
  mysql.db.name: "nacos_devtest"
  mysql.port: "3306"
  mysql.user: "nacos"
  mysql.password: "passwd"
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: nacos
spec:
  serviceName: nacos-headless
  replicas: 3
  template:
    metadata:
      labels:
        app: nacos
      annotations:
        pod.alpha.kubernetes.io/initialized: "true"
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchExpressions:
                  - key: "app"
                    operator: In
                    values:
                      - nacos
              topologyKey: "kubernetes.io/hostname"
      containers:
        - name: k8snacos
          imagePullPolicy: Always
          image: nacos/nacos-server:latest
          resources:
            requests:
              memory: "2Gi"
              cpu: "500m"
          ports:
            - containerPort: 8848
              name: client
            - containerPort: 9848
              name: client-rpc
            - containerPort: 9849
              name: raft-rpc
            - containerPort: 7848
              name: old-raft-rpc
          env:
            - name: NACOS_REPLICAS
              value: "3"
            - name: MYSQL_SERVICE_HOST
              valueFrom:
                configMapKeyRef:
                  name: nacos-cm
                  key: mysql.host
            - name: MYSQL_SERVICE_DB_NAME
              valueFrom:
                configMapKeyRef:
                  name: nacos-cm
                  key: mysql.db.name
            - name: MYSQL_SERVICE_PORT
              valueFrom:
                configMapKeyRef:
                  name: nacos-cm
                  key: mysql.port
            - name: MYSQL_SERVICE_USER
              valueFrom:
                configMapKeyRef:
                  name: nacos-cm
                  key: mysql.user
            - name: MYSQL_SERVICE_PASSWORD
              valueFrom:
                configMapKeyRef:
                  name: nacos-cm
                  key: mysql.password
            - name: MODE
              value: "cluster"
            - name: NACOS_SERVER_PORT
              value: "8848"
            - name: PREFER_HOST_MODE
              value: "hostname"
            - name: NACOS_SERVERS
              value: "nacos-0.nacos-headless.default.svc.cluster.local:8848 nacos-1.nacos-headless.default.svc.cluster.local:8848 nacos-2.nacos-headless.default.svc.cluster.local:8848"
  selector:
    matchLabels:
      app: nacos




---
# ------------------- App Ingress ------------------- #
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: nacos-headless
  namespace: default

spec:
  rules:
  - host: nacos-web.nacos-demo.com
    http:
      paths:
      - path: /
        backend:
          serviceName: nacos-headless
          servicePort: server

```


