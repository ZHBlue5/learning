### service资源类型
```yaml
kind: Service
apiVersion: v1
metadata:
  name: test-svc
spec:
  selector:
    app: test1
  ports:
  - protocol: TCP
    port: 80
    targetPort: 80
```
- 默认情况下Service的资源类型Cluster IP，上述YAML文件中，spec.ports.port:描述的是Cluster IP的端口。只是为后端的Pod提供了一个统一的访问入口（在k8s集群内有效）
- 如果想要让外网能够访问到后端Pod，这里应该将Service的资源类型改为NodePort。
```yaml
kind: Service
apiVersion: v1
metadata:
  name: test-svc
spec:
  type: NodePort
  selector:
    app: test1
  ports:
  - protocol: TCP
    port: 80
    targetPort: 80
    nodePort: 32034  #nodePort的有效范围是：30000-32767
```

### namespace简介
namespace资源对象仅用于资源对象的隔离，并不能隔绝不同名称空间的Pod之间的通信，那是网络策略资源的功能


### Pod的默认健康检查
##### livenessprobe(活跃度、存活性)
- Liveness活跃度探测，根据探测某个文件是否存在，来确认某个服务是否正常运行，如果存在则正常，否则，它会根据你设置的Pod的重启策略操作Pod

##### readiness(敏捷探测、就绪性探测)

```text
(1)liveness和readiness是两种健康检查机制，如果不特意配置，k8s将两种探测采取相同的默认行为，即通过判断容器启动进程的返回值是否为零，来判断探测是否成功。

(2)两种探测配置方法完全一样，不同之处在于探测失败后的行为：
liveness探测是根据Pod重启策略操作容器，大多数是重启容器;
readiness则是将容器设置为不可用，不接收Service转发的请求;

(3)两种探测方法可以独立存在，也可以同时使用。
用liveness判断容器是否需要重启实现自愈；
用readiness判断容器是否已经准备好对外提供服务;

```


### ReplicaSet
- 用于确保由其管控的Pod对象副本数量，能够满足用户期望，多则删除，少则通过模板创建
- 通过yaml或json格式的资源清单来创建。其中spec字段一般嵌套以下字段
```yaml
replicas:期望的Pod对象副本数量;

selector:当前控制器匹配Pod对象副本的标签选择器;

template:Pod副本的模板;

```
- 标签有多个，标签选择器选择其中一个，也可以关联成功。相反，如果选择器有多个，那么标签必须完全满足条件，才可以关联成功
```text
matchLabels:指定键值对表示的标签选择器;

matchExpressions:基于表达式来指定的标签选择器;

```
```yaml
...
selector:
  matchLabels:
    app: nginx
  matchExpressions:
  #pod中有name这个关键字的，并且对应的值是:zhangsan或者lisi，那么此处会关联到Pod1、Pod2
    - {key: name,operator: In,values: [zhangsan,lisi]}
  #所有关键字是age的，并且忽略它的值，都会被选中，此处会关联到Pod1、Pod2、Pod3 
    - {key: age,operator: Exists,values:}
...

```