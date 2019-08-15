## 摘要
*本文对几种流行的 API 网关以关键指标 RPS 为依据，利用 Apache Benchmark 做了性能测评并且给出结论。本文所有使用的软件、命令、以及代码均在文中注明，读者可以很方便地在自己的环境中做出相同的测试。另外性能测试的数据在不同的运行环境中差别较大，但是总体上来说各项数据会成比例变化，本文的测试过程和结论可以较准确地反应出各 API 网关之间的性能差异*
## 背景知识介绍
### API 网关
//TODO: API 网关，主要用于...

#### Netflix Zuul
//TODO: Zuul, 诞生于 Netflix 公司...后来被集成到 Spring Cloud...
#### Spring Cloud Gateway
//TODO: Spring Cloud Gateway 为...
#### ServiceComb EdgeService
//TODO: 相比以上的两个网关，EdgeService 可能知名度没那么大，但其实 EdgeService 也来头不小...

## 性能测试
> 为保证此次测试的结果数据可靠，本次测试没有引入任何如 Euraka 等服务发现机制，遵从最少依赖的原则，只对网关本身的性能进行测评
### 环境准备：
- 硬件环境：
  - CPU: 双核四线程
  - 内存：8 GB
- 软件环境：
  - Apache Benchmark

### 工程文件：
> 本文涉及到的所有代码可从 https://github.com/AngLi2/api-gateway-benchmark 获得
//TODO: github 工程截图
- origin：为本次性能测试的被调用服务文件，测试中在 *8080* 端口启动
- zuul: 为 zuul 网关程序，将请求分发到 origin，测试中在 *8081* 端口启动
  - 使用的 spring-cloud-starter-zuul 版本为 1.4.7.RELEASE，对应的 Zuul 版本为 1.3.1
- gateway: 为 gateway 网关程序，将请求分发到 origin，测试中在 *8082* 端口启动
  - 使用的 spring-cloud-starter-gateway 版本为 2.1.2.RELEASE
- edgeservice: 为 edgeservice 网关程序，将请求分发到 origin，测试中在 *8083* 端口启动
  - 使用的 org.apache.servicecomb:edge-core 版本为 1.2.0.B006

### 关键配置：
> 这里展示了多种不同的配置方法，起到的效果都是一样的，不影响网管的使用和性能
#### Netflix Zuul:
通过 application.properties 进行配置：
```yml
zuul.routes.demo.path=/*
zuul.routes.demo.url=http://localhost:8081
```
#### Spring Cloud Gateway:
通过 Bean 注入的方式进行配置：
```java
@Bean
public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
    return builder.routes()
            .route(r -> r.path("/checked-out")
                    .uri("http://localhost:8080/checked-out")
            ).build();
}
```
#### ServiceComb EdgeService
由于 EdgeService 主要是针对微服务，为了保证控制变量，需要先注册 REST 服务的 endpoint、接口契约等信息，所以这里的配置稍显复杂，如果直接使用微服务，会变得简单很多。
1. 根据 REST 接口编写 Java 接口类
  ```Java
  @Path("/checked-out")
  @Api(produces = MediaType.TEXT_PLAIN)
  public interface Service {
      @GET
      String getCheckedOut();
  }
  ```
2. 根据 endpoint 调用方法并且注册：
```java
String endpoints="rest://127.0.0.1:8080";
RegistryUtils.getServiceRegistry().registerMicroserviceMappingByEndpoints(
        "thirdPartyService",
        "0.0.1",
        Collections.singletonList(endpoints),
        Service.class
);
```
3. 网关配置文件(这里的 loadbalance 只是为了 edgeservice 调用，实际只有一个被调用实例)：
```yml
servicecomb:
  rest:
    address: 127.0.0.1:8083
  handler:
    chain:
      Consumer:
        default: loadbalance
  http:
    dispatcher:
      edge:
        default:
          withVersion: false
          enabled: true
          prefix: rest
          prefixSegmentCount: 2
```
### 短链接测试过程：
#### 对原始服务进行测试：
1. 运行命令: `ab -n 100000 -c 100 http://localhost:8080/checked-out/`
2. 得到结果如下：
  ```yml
  Time taken for tests:   15.221 seconds
  Complete requests:      100000
  Failed requests:        0
  Total transferred:      15400000 bytes
  HTML transferred:       2100000 bytes
  Requests per second:    6570.00 [#/sec] (mean)
  Time per request:       15.221 [ms] (mean)
  Time per request:       0.152 [ms] (mean, across all concurrent requests)
  Transfer rate:          988.07 [Kbytes/sec] received

  Connection Times (ms)
                min  mean[+/-sd] median   max
  Connect:        0    0   0.3      0       5
  Processing:     2   15   1.7     15      93
  Waiting:        2   11   2.7     12      87
  Total:          2   15   1.7     15      93

  Percentage of the requests served within a certain time (ms)
    50%     15
    66%     16
    75%     16
    80%     16
    90%     16
    95%     17
    98%     18
    99%     18
   100%     93 (longest request)
  ```
3. 提取 RPS 数据，可以得到原始服务调用测试 RPS 为：6570.00 请求/秒
#### Netflix Zuul
1. 运行命令: `ab -n 100000 -c 100 http://localhost:8081/checked-out/`
2. 得到结果如下：
  ```yml
  Time taken for tests:   26.780 seconds
  Complete requests:      100000
  Failed requests:        0
  Total transferred:      13300000 bytes
  HTML transferred:       0 bytes
  Requests per second:    3734.09 [#/sec] (mean)
  Time per request:       26.780 [ms] (mean)
  Time per request:       0.268 [ms] (mean, across all concurrent requests)
  Transfer rate:          484.99 [Kbytes/sec] received

  Connection Times (ms)
                min  mean[+/-sd] median   max
  Connect:        0    0   0.3      0       3
  Processing:     0   27  61.1      2     439
  Waiting:        0   26  61.0      2     438
  Total:          0   27  61.1      2     439

  Percentage of the requests served within a certain time (ms)
    50%      2
    66%      3
    75%      4
    80%      5
    90%    151
    95%    172
    98%    201
    99%    233
   100%    439 (longest request)
  ```
3. 提取 RPS 数据，可以得到 Zuul 的测试 RPS 为：3734.09 请求/秒
#### Spring Cloud Gateway
1. 运行命令: `ab -n 100000 -c 100 http://localhost:8082/checked-out/`
2. 得到结果如下：
  ```yml
  Time taken for tests:   47.666 seconds
  Complete requests:      100000
  Failed requests:        0
  Total transferred:      15600000 bytes
  HTML transferred:       2100000 bytes
  Requests per second:    2097.93 [#/sec] (mean)
  Time per request:       47.666 [ms] (mean)
  Time per request:       0.477 [ms] (mean, across all concurrent requests)
  Transfer rate:          319.61 [Kbytes/sec] received

  Connection Times (ms)
                min  mean[+/-sd] median   max
  Connect:        0    0   0.4      0      17
  Processing:     2   47  10.3     46     363
  Waiting:        2   47  10.2     45     363
  Total:          3   48  10.3     46     363

  Percentage of the requests served within a certain time (ms)
    50%     46
    66%     48
    75%     50
    80%     52
    90%     57
    95%     62
    98%     73
    99%     84
   100%    363 (longest request)
  ```
3. 提取 RPS 数据，可以得到 Gateway 的测试 RPS 为：2097.93 请求/秒
#### ServiceComb EdgeService
1. 运行命令: `ab -n 100000 -c 100 http://localhost:8083/rest/thirdPartyService/checked-out/`
2. 得到结果如下：
  ```yml
  Time taken for tests:   39.571 seconds
  Complete requests:      100000
  Failed requests:        0
  Total transferred:      13618848 bytes
  HTML transferred:       2100000 bytes
  Requests per second:    2527.09 [#/sec] (mean)
  Time per request:       39.571 [ms] (mean)
  Time per request:       0.396 [ms] (mean, across all concurrent requests)
  Transfer rate:          336.09 [Kbytes/sec] received

  Connection Times (ms)
                min  mean[+/-sd] median   max
  Connect:        0    0   0.3      0      18
  Processing:     0   38 285.7     18    9094
  Waiting:        0   37 278.3     18    9093
  Total:          0   38 285.7     18    9094

  Percentage of the requests served within a certain time (ms)
    50%     18
    66%     32
    75%     44
    80%     52
    90%     73
    95%     86
    98%    127
    99%    156
   100%   9094 (longest request)
  ```
3. 提取 RPS 数据，可以得到 EdgeService 的测试 RPS 为：2527.09 请求/秒
#### 同时进行抢占式性能测试
//TODO: 如果我们能体现出优势，则增加这一部分

### 长链接测试过程：
>由于在压测过程中，长连接的 RPS 肯定优于短链接，所以对长链接进行压力测试，但是 Spring Cloud Gateway 长链接在 ab 测试无响应，经查询发现是因为 Reactor Netty 不支持 HTTP 1.0，而 Spring Cloud Gateway 依赖了 reactor-netty，所以 ab 测试并不准确，所以仅对 Zuul 和 EdgeService 进行长链接的压力测试，在 Apache Benchmark 中，只需加上参数 -k 即可进行长链接模式，后续会使用 wrk 进行测试。
#### 对原始服务进行测试：
1. 运行命令: `ab -k -n 100000 -c 100 http://localhost:8080/checked-out/`
2. 得到结果如下：
  ```yml
  Time taken for tests:   11.408 seconds
  Complete requests:      100000
  Failed requests:        0
  Keep-Alive requests:    99050
  Total transferred:      15895250 bytes
  HTML transferred:       2100000 bytes
  Requests per second:    8765.63 [#/sec] (mean)
  Time per request:       11.408 [ms] (mean)
  Time per request:       0.114 [ms] (mean, across all concurrent requests)
  Transfer rate:          1360.66 [Kbytes/sec] received

  Connection Times (ms)
                min  mean[+/-sd] median   max
  Connect:        0    0   0.0      0       7
  Processing:     0   11 111.7      2    3609
  Waiting:        0   11 111.7      2    3609
  Total:          0   11 111.7      2    3609

  Percentage of the requests served within a certain time (ms)
    50%      2
    66%     14
    75%     15
    80%     15
    90%     17
    95%     19
    98%     22
    99%     25
   100%   3609 (longest request)
  ```
3. 提取 RPS 数据，可以得到原始服务调用测试 RPS 为：8765.63 请求/秒
#### Netflix Zuul
1. 运行命令: `ab -k -n 100000 -c 100 http://localhost:8081/checked-out/`
2. 得到结果如下：
  ```yml
  Time taken for tests:   18.882 seconds
  Complete requests:      100000
  Failed requests:        0
  Keep-Alive requests:    99047
  Total transferred:      13795235 bytes
  HTML transferred:       0 bytes
  Requests per second:    5296.09 [#/sec] (mean)
  Time per request:       18.882 [ms] (mean)
  Time per request:       0.189 [ms] (mean, across all concurrent requests)
  Transfer rate:          713.48 [Kbytes/sec] received

  Connection Times (ms)
                min  mean[+/-sd] median   max
  Connect:        0    0   0.0      0       1
  Processing:     0   19  53.1      1     704
  Waiting:        0   19  53.1      1     704
  Total:          0   19  53.1      1     704

  Percentage of the requests served within a certain time (ms)
    50%      1
    66%      1
    75%      1
    80%      1
    90%    101
    95%    127
    98%    179
    99%    209
   100%    704 (longest request)
  ```
3. 提取 RPS 数据，可以得到 Zuul 的测试 RPS 为：5296.09 请求/秒
#### ServiceComb EdgeService
1. 运行命令: `ab -n 100000 -c 100 http://localhost:8083/rest/thirdPartyService/checked-out/`
2. 得到结果如下：
  ```yml
  Time taken for tests:   15.492 seconds
  Complete requests:      100000
  Failed requests:        0
  Keep-Alive requests:    100000
  Total transferred:      16000000 bytes
  HTML transferred:       2100000 bytes
  Requests per second:    6454.86 [#/sec] (mean)
  Time per request:       15.492 [ms] (mean)
  Time per request:       0.155 [ms] (mean, across all concurrent requests)
  Transfer rate:          1008.57 [Kbytes/sec] received

  Connection Times (ms)
                min  mean[+/-sd] median   max
  Connect:        0    0   0.0      0       6
  Processing:     2   14  14.0     13     747
  Waiting:        1   14  14.0     13     747
  Total:          2   14  14.0     13     747

  Percentage of the requests served within a certain time (ms)
    50%     13
    66%     14
    75%     16
    80%     17
    90%     20
    95%     24
    98%     37
    99%     64
   100%    747 (longest request)
  ```
3. 提取 RPS 数据，可以得到 EdgeService 的测试 RPS 为：6454.86 请求/秒
#### 同时进行抢占式性能测试
//TODO: 如果我们能体现出优势，则增加这一部分
### 测试结果：
对测试的数据进行表格分析对比，表格如下图所示：
//TODO: 分别绘制长连接，短链接时，性能对比图，从裸数据和百分比两个角度绘制

## 结论：
可以看出，在同等测试环境下，网关之间的性能差别很大，其中，EdgeService 的性能优势明显，在短链接情况下，通过 EdgeService 访问服务仅比直接访问原服务损失 x% 的性能，而 Zuul 的性能损失比 EdgeService 多出 x%，Gateway 则多出 x%。在长链接情况下，通过 EdgeService 访问服务仅比直接访问原服务损失 x% 的性能，而 Zuul 的性能损失比 EdgeService 多出 x%，Gateway 则多出 x%。
