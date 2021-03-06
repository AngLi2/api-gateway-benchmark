*关键字：网关，Zuul，Gateway，Spring Cloud, ServiceComb，Edge Service性能测试，微服务*

## 导读
>本文对几种流行的 API 网关以关键指标 RPS 为依据，利用 wrk 做出性能测评并且给出结论。本文所有使用的软件、命令、以及代码均在文中注明，以便读者搭建本地环境进行测试。注意性能测试的数据在不同的运行环境中差别较大，但总体上来说各项数据会成比例变化，本文的测试过程和结论可以较准确地反应出各 API 网关之间的性能差异。

## 背景知识介绍
### API 网关
近些年来，在云时代的背景下，为了适应互联网和大数据的高速发展，随着微服务架构的持续火热，对 API 网关的诉求越来越强烈，API 网关的产品也层出不穷。除了传统的 Zuul 和 SpringCloud Gateway, 还诞生了很多优秀的网关，本文选取了Edge Service 作为比较对象与传统的网关进行了 API 网关的性能测评。

究竟是久经沙场的老牌网关更经得起考验，还是新兴的网关性能更优？本文将给出详细的测评过程和结果。

#### Netflix Zuul
![netflix](https://github.com/AngLi2/api-gateway-benchmark/blob/master/img/netflix.jpg)

Zuul 在这三个网关中是最早诞生的，其 github repo 早在 2013 年之前就已经存在，同年开始进入大众视野，崭露头角。虽然 Zuul 诞生较早，也占据着不小的市场份额，但由于 Zuul本身是基于阻塞io开发的，在如今的网关市场上，相较其他的产品，性能上稍显马力不足。

#### Spring Cloud Gateway
![spring](https://github.com/AngLi2/api-gateway-benchmark/blob/master/img/spring.jpg)

Gateway 建立在 Spring Framework 5，Project Reactor 和 Spring Boot 2 上，不同于 Zuul 的阻塞 IO，Gateway使用的是非阻塞 IO，相较 Zuul 具备更好的内核性能；同时与Spring紧密集成，对于开发者而言，成为了一个整合方便，使用方便，性能高的产品，有着良好的生态市场作为依托。

其实，Spring Cloud 最开始是整合 Zuul 作为网关解决方案，但是随着时间的推移，BIO 的局限性不断暴露，捉襟见肘，Spring 开始考虑另寻他路。自此，Spring Cloud Gateway 网关亮相面世。而这一产品也确实经受住了时间的考验，成为了业界最佳的网关选择之一。

#### ServiceComb EdgeService
![servicecomb](https://github.com/AngLi2/api-gateway-benchmark/blob/master/img/servicecomb.png)

EdgeService 来自于 Apache 开源项目 apache/servicecomb-java-chassis，其主项目 Apache ServiceComb 是由华为公司于2017年捐献给 Apache孵化，并于次年 10 月 24 日宣布毕业，也是业界首个在Apache 孵化毕业的顶级开源微服务项目。

在如今的云原生时代背景下， EdgService 能很好的适应发展变革与鹿场角逐，由于自带微服务场景的基因，所以 EdgeService 天生适用于在微服务场景，并且和 ServiceComb-Java-Chassis 完美集成，更好的融入微服务项目，具体信息可以参考 EdgeService文档。

## 性能测试
### 环境准备：
- 硬件环境：三台机器，分别运行服务端程序，网关程序和压测程序
  - CPU:  4vCPU Intel(R) Xeon(R) CPU E5-2680 v4 @ 2.40GHz
  - 内存：8GB
- 软件环境：
  - wrk

### 工程文件：
本文涉及到的所有代码可从 https://github.com/AngLi2/api-gateway-benchmark 获得：
![github_project_tree](https://github.com/AngLi2/api-gateway-benchmark/blob/master/img/github_project_tree.png)
其中：
- origin：为本次性能测试的被调用服务文件，测试中在 *192.168.0.5:8080* 端口启动
- zuul: 为 zuul 网关程序，将请求分发到 origin，测试中在 *192.168.0.152:8081* 端口启动
  - 使用的 spring-cloud-starter-zuul 版本为 1.4.7.RELEASE，对应的 Zuul 版本为 1.3.1
- gateway: 为 gateway 网关程序，将请求分发到 origin，测试中在 *192.168.0.152:8082* 端口启动
  - 使用的 spring-cloud-starter-gateway 版本为 2.1.2.RELEASE
- edgeservice: 为 edgeservice 网关程序，将请求分发到 origin，测试中在 *192.168.0.152:8083* 端口启动
  - 使用的 org.apache.servicecomb:edge-core 版本为 1.2.0.B006

### 关键配置：
> 这里展示了多种不同的配置方法，最终效果均一致，不影响网关的使用和性能
#### Netflix Zuul:
通过 application.properties 进行配置：
```yml
zuul.routes.demo.path=/*
zuul.routes.demo.url=http://192.168.0.5:8080
```
#### Spring Cloud Gateway:
通过 Bean 注入的方式进行配置：
```java
@Bean
public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
    return builder.routes()
            .route(r -> r.path("/checked-out")
                    .uri("http://192.168.0.5:8080/checked-out")
            ).build();
}
```
#### ServiceComb EdgeService
由于 EdgeService 主要是针对微服务，需要先注册 REST 服务的 endpoint、接口契约等信息，所以这里的配置稍显复杂，如果直接使用 ServiceComb 的微服务解决方案（服务中心，配置中心）等，会方便很多，感兴趣的同学可以参考《21天微服务实践》中的网关部分实践一下。
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
    String endpoints="rest://192.168.0.5:8080";
    RegistryUtils.getServiceRegistry().registerMicroserviceMappingByEndpoints(
            "thirdPartyService",
            "0.0.1",
            Collections.singletonList(endpoints),
            Service.class
    );
    ```
3. 网关配置文件(这里的 loadbalance 只是为了 edgeservice 调用，实际只有一个被调用实例)，此处 maxPoolSize 设置成了20，原因如下：

    在没有进行 maxPoolSize 的设置的时候，使用 ` netstat -apn | grep 192.168.0.5 | grep ESTA | wc -l` 查询连接数，发现只建立了 20 条连接。参考 [ServiceComb 文档](https://docs.servicecomb.io/java-chassis/zh_CN/transports/rest-over-vertx.html)，可以看出链接数为 verticle-count * maxPoolSize，而 maxPoolSize 的默认值只有5，而 verticle-count 的默认值为：

    - 如果CPU数小于8，则取CPU数
    - 如果CPU数大于等于8，则为8

    因为测试环境的 CPU 读数为4，所以总链接数只有 4 * 5 = 20，而 Spring Cloud Gateway 的总链接数测试的时候一直在 90 多，所以这里为了保证测评公平有效，将 maxPoolSize 设置成 20。

    > 事实上，即使在 EdgeService 的链接数为 20 的情况下，测试时也比 Spring Cloud Gateway 链接数 90 的表现要好一点，读者们可以自己尝试一下。

    ```yml
    servicecomb:
      rest:
        address: 127.0.0.1:8083
      client:
        connection:
          maxPoolSize: 20
      http:
        dispatcher:
          edge:
            default:
              withVersion: false
              enabled: true
              prefix: rest
              prefixSegmentCount: 2
    ```
### 测试过程：
早期方案使用 Apache Benchmark 进行压力测试，得出的结论和预期的大相径庭，基于 rxNetty 的 Spring Cloud Gateway 比 Zuul 表现还差一大截，而且在进行长连接测试的时候 Spring Cloud Gateway 直接卡死。

这个问题在 github 的 spring-cloud-gateway 的 Issues 区早有提及： [Throughput problems when compared with Netflix Zuul and Nginx](https://github.com/spring-cloud/spring-cloud-gateway/issues/124)

Issue 被提出是因为有人提出用 Apache Benchmark 对 Spring Cloud Gateway, Netflix Zuul 和 Nginx 进行压测，发现结果如下：

Experiment | Mean Time Per Request (ms) | Request Per Second
-|-|-
Nginx reverse proxy | 32.085 | 6233.40
Zuul (after warmup) | 28.422 | 7036.90
Spring Cloud Gateway | 229.058 | 873.14

通过 Issue 的回复区的: [Add support for HTTP 1.0](https://github.com/reactor/reactor-netty/issues/21) 关联问题，我们可以找到很多关于 rxNetty 不支持 HTTP1.0 的 Issues，这里列出来几个，有兴趣的读者可以点进去看看：
- [Add HTTP 1.0 support](https://github.com/ReactiveX/RxNetty/issues/575)
- [Buffering of output in Spring Web Reactive with Netty too aggressive](https://github.com/spring-projects/spring-framework/issues/19510)
- [Add HTTP 1.0 support on Reactor Netty](https://github.com/spring-projects/spring-framework/issues/19531)

至此，得出 Apache Benchmark 并不能很好地测试出网关的性能，转而使用 wrk 进行测试（Spring Cloud Gateway 官方使用的性能测试工具也是这个，后文会有提及）。wrk 默认工作于长连接模式，有效地减少了断连建连的损耗，可以比较真实地反映出网关的性能。

#### 对原始服务进行测试：
1. 运行命令: `wrk -t12 -c100 -d300s http://192.168.0.5:8080/checked-out`
2. 得到结果如下：
  ```yml
  Running 5m test @ http://192.168.0.5:8080/checked-out
    12 threads and 100 connections
    Thread Stats   Avg      Stdev     Max   +/- Stdev
      Latency     2.94ms    1.18ms  56.41ms   81.59%
      Req/Sec     2.76k   228.24     3.76k    72.32%
    9906220 requests in 5.00m, 1.25GB read
  Requests/sec:  33014.70
  Transfer/sec:      4.26MB
  ```
3. 根据测试结果，可以得到延时服务的 RPS 和平均延时为：
   - RPS：33014.70 请求/秒
   - Average Latency: 2.94ms
#### Netflix Zuul
1. 运行命令: `wrk -t12 -c100 -d300s http://192.168.0.5:8081/checked-out`
2. 得到结果如下：
  ```yml
  Running 5m test @ http://192.168.0.152:8081/checked-out
    12 threads and 100 connections
    Thread Stats   Avg      Stdev     Max   +/- Stdev
      Latency    12.39ms   21.27ms   1.15s    91.90%
      Req/Sec     1.10k   264.62     2.09k    72.43%
    3953807 requests in 5.00m, 735.99MB read
  Requests/sec:  13175.21
  Transfer/sec:      2.45MB
  ```
3. 根据测试结果，可以得到 Netflix Zuul 的 RPS 和平均延时为：
   - RPS：13175.21 请求/秒
   - Average Latency: 12.39ms
4. 在性能测试的过程中使用 top 看一下 CPU 使用情况，基本满负载运行：
  ![zuul_cpu](https://github.com/AngLi2/api-gateway-benchmark/blob/master/img/zuul_cpu.png)

#### Spring Cloud Gateway
1. 运行命令: `wrk -t12 -c100 -d300s http://192.168.0.152:8082/checked-out`
2. 得到结果如下：
  ```yml
  Running 5m test @ http://192.168.0.152:8082/checked-out
    12 threads and 100 connections
    Thread Stats   Avg      Stdev     Max   +/- Stdev
      Latency     4.95ms    9.96ms 539.29ms   98.96%
      Req/Sec     1.82k   222.74     2.39k    91.81%
    6507221 requests in 5.00m, 850.19MB read
  Requests/sec:  21685.14
  Transfer/sec:      2.83MB
  ```
3. 根据测试结果，可以得到 Spring Cloud Gateway 的 RPS 和平均延时为：
   - RPS：21685.14 请求/秒
   - Average Latency: 4.95ms
4. 在性能测试的过程中使用 top 看一下 CPU 使用情况，基本满负载运行：
  ![gateway_cpu](https://github.com/AngLi2/api-gateway-benchmark/blob/master/img/gateway_cpu.png)

#### ServiceComb EdgeService
1. 运行命令: `wrk -t12 -c100 -d300s http://192.168.0.152:8083/rest/thirdPartyService/checked-out`
2. 得到结果如下：
  ```yml
  Running 5m test @ http://192.168.0.152:8083/rest/thirdPartyService/checked-out
    12 threads and 100 connections
    Thread Stats   Avg      Stdev     Max   +/- Stdev
      Latency     3.80ms    4.67ms 300.59ms   97.98%
      Req/Sec     2.27k   309.82     3.10k    86.53%
    8144028 requests in 5.00m, 1.03GB read
  Requests/sec:  27139.19
  Transfer/sec:      3.52MB
  ```
3. 提取 RPS 数据，可以得到 EdgeService 的测试 RPS 为：27139.19 请求/秒
4. 在性能测试的过程中使用 top 看一下 CPU 使用情况，基本满负载运行：
  ![edgeservice_cpu](https://github.com/AngLi2/api-gateway-benchmark/blob/master/img/edgeservice_cpu.png)

### 测试结果：
对测试的数据进行表格分析对比，分别给出平均时延，RPS 和性能损失（（原服务的 RPS - 网关的 RPS） / 原服务的 RPS）表格如下图所示：

服务 | 平均时延(ms) | RPS | 性能损失
-|-|-|-
Origin  | 2.94 | 33014.70 | 0
Netflix Zuul | 12.39 | 13175.21 | 60.09%
Spring Cloud Gateway | 4.95 | 21685.14 | 34.32%
ServiceComb EdgeService | 4.01 | 27139.19 | 17.80%

可以看出，在硬件环境完全相同，并且 cpu 消耗基本一致的情况下，以 RPS 为性能指标（以性能损失为性能指标的话，差异更大，这里参考业界做法，以 RPS 为指标），ServiceComb EdgeService 的性能是 Netflix Zuul 的两倍多，是 Spring Cloud Gateway 的 1.25 倍多，这还是在 EdgeService 的链接数劣于 Spring Cloud Gateway 20% 左右的情况下的数据，如果将 EdgeService 的链接数设置和 Spring Cloud Gateway 一致，性能会相差更大，有兴趣的读者可以自己尝试一下。

## 结论：
Spring Cloud Gateway 的性能比 Zuul 好基本上已经是业界公认的了，实际上，Spring Cloud Gateway 官方也发布过一个性能测试：[spring-cloud-gateway-bench](https://github.com/spencergibb/spring-cloud-gateway-bench)，这里节选数据如下：

Proxy |	Avg Latency |	Avg Req
-|-|-
gateway | 6.61ms | 3.24k
linkered  | 7.62ms | 2.82k
zuul | 12.56ms | 2.09k
none | 2.09ms | 11.77k

因为我们的测试机器部署在同一个局域网，所以性能损失均要低于 spring-cloud-gateway-bench 的测试数据，但是从测试结果看来基本一致。网关的性能和其实现方式也有很大的关系：
- Netflix Zuul: 测试版本为 1.x，基于阻塞 io
- Spring Cloud Gateway: 前面已经提到过，基于 RxNetty，异步非阻塞
- ServiceComb EdgeService：为 ServiceComb 的子项目，基于 vert.x，也是异步非阻塞

同样基于异步非阻塞，EdgeService 的性能明显优于 Spring Cloud Gateway，可以看出网关的性能不仅和底层实现有关，和内部实现方式和优化也有很大的关系。参考 ServiceComb 的[官方文档](https://docs.servicecomb.io/java-chassis/zh_CN)，可以发现 EdgeService 还支持接入 rest 自动变成 highway 转调，性能更高。这里因为协议层面不一样，就不放出来做对比了，对性能有极致要求的可以采用这种模式。

在 2018 年终于发布了 Zuul 2.x 之后，Netflix 给出了一个比较模糊的数据，大致 Zuul2 的性能比 Zuul1 好 20% 左右。然而从测试数据看来即使性能提升一半也完全比不上 Spring Cloud Gateway 的，更不用说 EdgeService 了。看来 Zuul 2.x 并没有把异步非阻塞的性能发挥出来。

竞争是发展的催化剂。在这个网关服务层出不穷的年代，各公司都铆足力气打造自己的网关产品，尽量让自己的产品成为用户的第一选择。而广大开发者也在享受这样的红利，使用高性能的网关来开发自己的应用。作为广大开发者的一员，我们欣然接受这样良性竞争的出现，并且也乐于尝试市面上出现的任何新产品，谁也说不准某一个产品以后就会成为优选的代名词。虽然从现在网关的性能差距看来，后发优势明显，但在可预见的将来，各网关迟早会到达性能瓶颈，在性能差距不大并且产品稳定之后，就会有各种差异化特性出现。而等到网关产品进入百舸争流的时代之后，用户就可以不再根据性能，而是根据自己的需求选择适合的网关服务了。

## 参考资料
- https://github.com/spring-cloud/spring-cloud-gateway/issues
- https://github.com/reactor/reactor-netty/issues
- https://github.com/ReactiveX/RxNetty/issues
- https://github.com/spring-projects/spring-framework/issues
- https://github.com/spencergibb/spring-cloud-gateway-bench
- https://docs.servicecomb.io/java-chassis/zh_CN
- http://www.itmuch.com/spring-cloud-sum/performance-zuul-and-gateway-linkerd/
- https://blog.csdn.net/j3T9Z7H/article/details/81025180
- https://www.zhihu.com/question/67498050
- https://www.jianshu.com/p/52c2fd448f24
- https://www.w3xue.com/exp/article/20197/48191.html
