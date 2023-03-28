### 创建 ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: shenyu-cm
  namespace: tencent
data:
  shenyu-admin-application.yml: |
    server:
      port: 9095
      address: 0.0.0.0
    spring:
      profiles:
        active: mysql
      thymeleaf:
        cache: true
        encoding: utf-8
        enabled: true
        prefix: classpath:/static/
        suffix: .html
      mvc:
        pathmatch:
          matching-strategy: ant_path_matcher
    mybatis:
      config-location: classpath:/mybatis/mybatis-config.xml
      mapper-locations: classpath:/mappers/*.xml
    shenyu:
      register:
        registerType: http
        serverLists: #localhost:2181 #http://localhost:2379 #localhost:8848
        props:
          sessionTimeout: 5000
          connectionTimeout: 2000
          checked: true
          zombieCheckTimes: 5
          scheduledTime: 10
          nacosNameSpace: ShenyuRegisterCenter
      sync:
        websocket:
          enabled: true
          messageMaxSize: 10240
          allowOrigins: ws://gateway-admin-svc.tencent.svc.cluster.local:9095;ws://gateway-bootstrap-svc.tencent.svc.cluster.local:9195;
      ldap:
        enabled: false
        url: ldap://xxxx:xxx
        bind-dn: cn=xxx,dc=xxx,dc=xxx
        password: xxxx
        base-dn: ou=xxx,dc=xxx,dc=xxx
        object-class: person
        login-field: cn
      jwt:
        expired-seconds: 86400000
      shiro:
        white-list:
          - /
          - /favicon.*
          - /static/**
          - /index**
          - /platform/login
          - /websocket
          - /error
          - /actuator/health
          - /swagger-ui.html
          - /webjars/**
          - /swagger-resources/**
          - /v2/api-docs
          - /csrf
      swagger:
        enable: false
      apidoc:
        gatewayUrl: http://127.0.0.1:9195
        envProps:
          - envLabel: Test environment
            addressLabel: Request Address
            addressUrl: http://127.0.0.1:9195
          - envLabel: Prod environment
            addressLabel: Request Address
            addressUrl: http://127.0.0.1:9195
    logging:
      level:
        root: info
        org.springframework.boot: info
        org.apache.ibatis: info
        org.apache.shenyu.bonuspoint: info
        org.apache.shenyu.lottery: info
        org.apache.shenyu: info
  shenyu-admin-application-mysql.yml: |
    shenyu:
      database:
        dialect: mysql
        init_script: "sql-script/mysql/schema.sql"
        init_enable: false
    spring:
      datasource:
        url: jdbc:mysql://mysql.css.dgut.edu.cn:3306/gateway?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&serverTimezone=Asia/Shanghai&useSSL=false
        username: shenyu
        password: 7brUgMAY8Hth5zqu
        driver-class-name: com.mysql.cj.jdbc.Driver
  shenyu-bootstrap-application.yml: |
    server:
      port: 9195
      address: 0.0.0.0
    spring:
      main:
        allow-bean-definition-overriding: true
        allow-circular-references: true
      application:
        name: shenyu-bootstrap
      codec:
        max-in-memory-size: 2MB
      cloud:
        discovery:
          enabled: false
        nacos:
          discovery:
            server-addr: 127.0.0.1:8848 # Spring Cloud Alibaba Dubbo use this.
            enabled: false
            namespace: ShenyuRegisterCenter
    eureka:
      client:
        enabled: false
        serviceUrl:
          defaultZone: http://localhost:8761/eureka/
      instance:
        prefer-ip-address: true
    management:
      health:
        defaults:
          enabled: false
    shenyu:
      matchCache:
        enabled: false
        maxFreeMemory: 256 # 256MB
      netty:
        http:
          # set to false, user can custom the netty tcp server config.
          webServerFactoryEnabled: true
          selectCount: 1
          workerCount: 4
          accessLog: false
          serverSocketChannel:
            soRcvBuf: 87380
            soBackLog: 128
            soReuseAddr: false
            connectTimeoutMillis: 10000
            writeBufferHighWaterMark: 65536
            writeBufferLowWaterMark: 32768
            writeSpinCount: 16
            autoRead: false
            allocType: "pooled"
            messageSizeEstimator: 8
            singleEventExecutorPerGroup: true
          socketChannel:
            soKeepAlive: false
            soReuseAddr: false
            soLinger: -1
            tcpNoDelay: true
            soRcvBuf: 87380
            soSndBuf: 16384
            ipTos: 0
            allowHalfClosure: false
            connectTimeoutMillis: 10000
            writeBufferHighWaterMark: 65536
            writeBufferLowWaterMark: 32768
            writeSpinCount: 16
            autoRead: false
            allocType: "pooled"
            messageSizeEstimator: 8
            singleEventExecutorPerGroup: true
      instance:
        enabled: false
        registerType: zookeeper #etcd #consul
        serverLists: localhost:2181 #http://localhost:2379 #localhost:8848
        props:
      cross:
        enabled: true
        allowedHeaders:
        allowedMethods: "*"
        allowedAnyOrigin: true # the same of Access-Control-Allow-Origin: "*"
        allowedExpose: ""
        maxAge: "18000"
        allowCredentials: true
      switchConfig:
        local: true
      file:
        enabled: true
        maxSize : 100
      sync:
        websocket:
          urls: ws://gateway-admin-svc.tencent.svc.cluster.local:9095/websocket
          allowOrigin: ws://gateway-bootstrap-svc.tencent.svc.cluster.local:9195
      exclude:
        enabled: false
        paths:
          - /favicon.ico
      fallback:
        enabled: false
        paths:
          - /fallback/hystrix
          - /fallback/resilience4j
      health:
        enabled: true
        paths:
          - /actuator/health
          - /health_check
      extPlugin:
        path:
        enabled: true
        threads: 1
        scheduleTime: 300
        scheduleDelay: 30
      scheduler:
        enabled: false
        type: fixed
        threads: 16
      upstreamCheck:
        enabled: false
        timeout: 3000
        healthyThreshold: 1
        unhealthyThreshold: 1
        interval: 5000
        printEnabled: true
        printInterval: 60000
      ribbon:
        serverListRefreshInterval: 10000
      metrics:
        enabled: false
        name : prometheus
        host: 127.0.0.1
        port: 8090
        jmxConfig:
        props:
          jvm_enabled: true
      local:
        enabled: false
        sha512Key: "BA3253876AED6BC22D4A6FF53D8406C6AD864195ED144AB5C87621B6C233B548BAEAE6956DF346EC8C17F5EA10F35EE3CBC514797ED7DDD3145464E2A0BAB413"
    logging:
      level:
        root: info
        org.springframework.boot: info
        org.apache.ibatis: info
        org.apache.shenyu.bonuspoint: info
        org.apache.shenyu.lottery: info
        org.apache.shenyu: info
```