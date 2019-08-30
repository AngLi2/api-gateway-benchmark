## 摘要
>本文对几种流行的 API 网关以关键指标 RPS 为依据，利用 wrk 做了性能测评并且给出结论。本文所有使用的软件、命令、以及代码均在文中注明，读者可以很方便地在自己的环境中做出相同的测试。另外性能测试的数据在不同的运行环境中差别较大，但是总体上来说各项数据会成比例变化，本文的测试过程和结论可以较准确地反应出各 API 网关之间的性能差异

## 背景知识介绍
### API 网关
网上有很多关于 API 网关的博客说得很清楚，比如 [使用 API Gateway](https://www.jianshu.com/p/52c2fd448f24) API 网关提供以下的一些功能：
- API 最核心的用途是提供系统的唯一入口，类似于设计模式中的“门面模式”，可以在网关层处理所有的非业务功能
- 封装系统内部架构，为每个客户端提供定制 API
- 还可以提供一些别的功能，如身份验证、监控、负载均衡、缓存、请求分片与管理、静态响应处理等。

#### Netflix Zuul
![netflix](https://github.com/AngLi2/api-gateway-benchmark/blob/master/img/netflix.jpg)

Zuul 在这三个网关中是最早诞生的，github 项目早在 2013 年之前就存在，而 2013 年就进入大众市场并广受欢迎，先发劣势导致当年的 Zuul 还是基于阻塞 io 开发的，在今天看来，性能实在是一般，但是先发优势又让 Zuul 在网关界一直有一席之地。

2016 年前后基于 NIO 的 Zuul2 开始开发，一直到 2018 年才发布，彼时，市场上类似产品层出不穷，Zuul 已经失去了它的先发优势，Spring Cloud 甚至到现在都没有对 Zuul2 提供支持，Spring Cloud Gateway 等产品的出现和 Zuul2 的频繁跳票，便秘式发布也让 Zuul 走下神坛，逐渐沦落为性能一般，需要被替换的代名词。

#### Spring Cloud Gateway
![spring](https://github.com/AngLi2/api-gateway-benchmark/blob/master/img/spring.jpg)

Gateway 建立在 Spring Framework 5，Project Reactor 和 Spring Boot 2 上，使用非阻塞 API。因为它与Spring紧密集成，对于开发者而言，成为了一个整合方便，使用方便，性能高的产品。

其实 Spring Cloud 最开始是整合 Zuul 作为网关解决方案的，但是随着时间的推移，AIO 的局限性不断暴露，据传 Spring 在等待高性能 Zuul 的过程中逐渐失去了耐心，直接导致 Spring Cloud 自己开发了 Spring Cloud Gateway 网关。而这一产品也确实经受住了时间的考验，成为了业界最佳的网关选择之一。回首往事看 Spring 等待 Zuul2 的过程，可以说是塞翁失马，焉知祸福了。

#### ServiceComb EdgeService
![servicecomb](https://github.com/AngLi2/api-gateway-benchmark/blob/master/img/servicecomb.png)

相比以上的两个网关，EdgeService 知名度显得小很多。但其实 EdgeService 来自于开源项目 apache/servicecomb-java-chassis，而 ServiceComb 在 2017 年 11 月由华为公司 捐献给 Apache 并启动孵化，并于同年 10 月 24 日被 Apache 宣布毕业成为 Apache 顶级项目，这也是业界首个微服务项目在 Apache 孵化并毕业成为顶级项目。

由于自带微服务场景的基因，所以 EdgeService 天生适用于在微服务场景，这一点在后文的配置部分可以很明显地感受的到。

## 性能测试
> 为保证此次测试的结果数据可靠，本次测试没有引入任何如 Euraka 等服务发现机制，遵从最少依赖的原则，只对网关本身的性能进行测评
### 环境准备：
- 硬件环境：三台机器，分别运行服务端程序，网关程序和压测程序
  - CPU: 4vCPU
  - 内存：8GB
- 软件环境：
  - wrk

### 工程文件：
本文涉及到的所有代码可从 https://github.com/AngLi2/api-gateway-benchmark 获得（顺便求下 star 嘿嘿嘿）：
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
> 这里展示了多种不同的配置方法，起到的效果都是一样的，不影响网关的使用和性能
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
由于 EdgeService 主要是针对微服务，需要先注册 REST 服务的 endpoint、接口契约等信息，所以这里的配置稍显复杂，如果直接使用微服务和服务中心，会方便很多。
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
3. 网关配置文件(这里的 loadbalance 只是为了 edgeservice 调用，实际只有一个被调用实例)，这里有一个 maxPoolSize 设置成了20，这个过程是这样的：

    开始没有进行 maxPoolSize 的设置，结果发现用 ` netstat -apn | grep 192.168.0.5 | grep ESTA | wc -l` 来看连接数发现只建立了 20 条连接，参考 [ServiceComb 文档](https://docs.servicecomb.io/java-chassis/zh_CN/transports/rest-over-vertx.html)，可以看出链接数为 verticle-count * maxPoolSize，而 maxPoolSize 的默认值只有5，而 verticle-count 的默认值为：

    - 如果CPU数小于8，则取CPU数
    - 如果CPU数大于等于8，则为8

    因为测试环境的 CPU 读数为4，所以总链接数只有 4 * 5 = 20，而 Spring Cloud Gateway 的总链接数测试的时候一直在 90 多，所以这里将 maxPoolSize 设置成 20。

    > 事实上，即使在 EdgeService 的链接数为 20 的情况下，测试时也比 Spring Cloud Gateway 链接数 90 的表现要好一点，读者们可以自己尝试一下。

    ```yml
    servicecomb:
      rest:
        address: 127.0.0.1:8083
      client:
        connection:
          maxPoolSize: 20
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
### 测试过程：
原先使用大名鼎鼎的 Apache Benchmark 进行压力测试，结果得出的结论和预期的大相径庭，基于 rxNetty 的 Spring Cloud Gateway 居然比 Zuul 表现还差一大截！而且在进行长连接测试的时候 Spring Cloud Gateway 直接卡死掉了。

其实这个问题在 github 的 spring-cloud-gateway 的 Issues 区早有提及： [Throughput problems when compared with Netflix Zuul and Nginx](https://github.com/spring-cloud/spring-cloud-gateway/issues/124)

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

既然得出 Apache Benchmark 并不能很好地测试出网关的性能，转而使用 wrk 进行测试（Spring Cloud Gateway 官方使用的性能测试工具也是这个，后面会有提及）。wrk 默认工作于长连接模式，有效地减少了断连建连的损耗，可以比较真实地反映出网关的性能。

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
#### ServiceComb EdgeService
1. 运行命令: `wrk -t12 -c100 -d300s http://192.168.0.152:8083/rest/thirdPartyService/checked-out`
2. 得到结果如下：
  ```yml
  Running 5m test @ http://192.168.0.152:8083/rest/thirdPartyService/checked-out
    12 threads and 100 connections
    Thread Stats   Avg      Stdev     Max   +/- Stdev
      Latency     4.01ms    8.08ms 425.44ms   98.83%
      Req/Sec     2.22k   316.11     3.12k    86.25%
    7958115 requests in 5.00m, 1.01GB read
  Requests/sec:  26519.67
  Transfer/sec:      3.44MB
  ```
3. 提取 RPS 数据，可以得到 EdgeService 的测试 RPS 为：26519.67 请求/秒

### 测试结果：
对测试的数据进行表格分析对比，分别给出平均时延，RPS 和性能损失（（原服务的 RPS - 网关的 RPS） / 原服务的 RPS）表格如下图所示：

服务 | 平均时延(ms) | RPS | 性能损失
-|-|-|-
Origin  | 2.94 | 33014.70 | 0
Netflix Zuul | 12.39 | 13175.21 | 60.09%
Spring Cloud Gateway | 4.95 | 21685.14 | 34.32%
ServiceComb EdgeService | 4.01 | 26519.67 | 19.67%

可以看出，在硬件环境完全相同的情况下，以 RPS 为性能指标（以性能损失为性能指标的话，差异更大，这里参考业界做法，以 RPS 为指标），ServiceComb EdgeService 的性能是 Netflix Zuul 的两倍多，是 Spring Cloud Gateway 的 1.22 倍多！这还是在 EdgeService 的链接数劣于 Spring Cloud Gateway 20% 左右的情况下的数据，如果将 EdgeService 的链接数设置和 Spring Cloud Gateway 一致，性能会相差更大，有兴趣的读者可以自己尝试一下。

## 结论：
Spring Cloud Gateway 的性能比 Zuul 好基本上已经是业界公认的了，实际上，Spring Cloud Gateway 官方也发布过一个性能测试：[spring-cloud-gateway-bench](https://github.com/spencergibb/spring-cloud-gateway-bench)，这里节选数据如下：

Proxy |	Avg Latency |	Avg Req
-|-|-
gateway  | 6.61ms  |  3.24k
linkered  | 7.62ms |  2.82k
zuul  | 12.56ms  |  2.09k
none  | 2.09ms  |  11.77k

因为我们的测试机器部署在同一个局域网，所以性能损失均要低于 spring-cloud-gateway-bench 的测试数据，但是从测试结果看来基本一致。网关的性能和其实现方式也有很大的关系：
- Netflix Zuul: 测试版本为 1.x，基于阻塞 io
- Spring Cloud Gateway: 前面已经提到过，基于 RxNetty，异步非阻塞
- ServiceComb EdgeService：为 ServiceComb 的子项目，基于 vert.x，也是异步非阻塞

同样基于异步非阻塞，EdgeService 的性能明显优于 Spring Cloud Gateway，可以看出网关的性能不仅和底层实现有关，和内部实现方式和优化也有很大的关系。

在 2018 年终于难产似的发布了 Zuul 2.x 之后，Netflix 给出了一个比较模糊的数据，大致 Zuul2 的性能比 Zuul1 好20%左右。然而从测试数据看来就算提升一半也完全打不过 Spring Cloud Gateway 的，更不用说 EdgeService 了。看来 Zuul 2.x 并没有把异步非阻塞的性能发挥出来。

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
