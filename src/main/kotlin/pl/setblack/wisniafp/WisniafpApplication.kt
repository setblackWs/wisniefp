package pl.setblack.wisniafp

import io.netty.channel.nio.NioEventLoopGroup
import org.reactivestreams.Publisher
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ReactiveHttpOutputMessage
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters.fromObject
import org.springframework.web.reactive.function.BodyInserters.fromPublisher
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono
import reactor.netty.http.server.HttpServer
import reactor.netty.tcp.TcpServer

class WisniaServer {
    fun start() {


        val route = router {
            GET("/fib/{n}") { request ->
                val n = Integer.parseInt(request.pathVariable("n"))
                println("Thread: " + Thread.currentThread().name)
                if (n < 2) {
                    ServerResponse.ok().contentType(MediaType.TEXT_HTML).body(fromObject<String>(n.toString()))
                } else {
                    val n_1 = WebClient.create("http://localhost:8080").get().uri("/fib/{n}", n - 1)
                            .accept(MediaType.TEXT_HTML).exchange()
                            .flatMap { resp -> resp.bodyToMono(String::class.java) }
                            .map<Int> { Integer.parseInt(it) }
                    val n_2 = WebClient.create("http://localhost:8080").get().uri("/fib/{n}", n - 2)
                            .accept(MediaType.TEXT_HTML).exchange().flatMap { resp -> resp.bodyToMono(String::class.java) }
                            .map<Int> { Integer.parseInt(it) }

                    val result = n_1
                            .flatMap { a -> n_2.map { b -> a + b } }
                            .map<String>({ it.toString() })
                    ServerResponse.ok().contentType(MediaType.TEXT_HTML).body(
                            fromPublisher<String, Mono<String>>(result, String::class.java))
                }
            }
            GET("/free", handle(::printHello))
            GET("/secret", secure(handleC(::printlHelloForUser)))

        }

        val httpHandler = RouterFunctions.toHttpHandler(route)
        val adapter = ReactorHttpHandlerAdapter(httpHandler)
        val eventLoopGroup = NioEventLoopGroup(1)
        val tcpServer = TcpServer
                .create()
                .host("0.0.0.0")
                .port(8080)
                .runOn(eventLoopGroup)

        val server = HttpServer
                .from(tcpServer)
                .handle(adapter)
                .bind()
                .block()!!

        readLine()

        server.disposeNow()
    }


}


fun printHello(req: ServerRequest): Mono<String> = Mono.just("hello")


fun printlHelloForUser(user: User): (ServerRequest) -> Mono<String> = { _ -> Mono.just("hello:" + user.name) }

fun main(args: Array<String>) {
    WisniaServer().start()
}

data class User(val name: String)

typealias Handler<T> = (ServerRequest) -> Mono<T>

typealias CHandler<C, R> = (C) -> R

//BodyInserter<P, ReactiveHttpOutputMessage>
inline fun <reified T> fromPublisher(publisher: Publisher<T>): BodyInserter<Publisher<T>, ReactiveHttpOutputMessage> =
        fromPublisher(publisher, T::class.java)


inline fun <reified T> handle(crossinline handler: Handler<T>): (ServerRequest) -> Mono<ServerResponse> {
    return { req: ServerRequest ->
        ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(
                fromPublisher(handler(req))
        )
    }
}

inline fun <C, reified T> handleC(crossinline handler: (C) -> (ServerRequest) -> Mono<T>): (C) -> (ServerRequest) -> Mono<ServerResponse> {
    return { c: C ->
        handle(handler(c))
    }
}


fun secure(handler: (User) -> (ServerRequest) -> Mono<ServerResponse>): (ServerRequest) -> Mono<ServerResponse> {
    return { req ->
        val authorizations = req.headers().header("Authorization")
        val domofon = authorizations.filter { it.startsWith("Domofon") }.firstOrNull()
        if (domofon == "Domofon to ja") {
            handler(User("toJa"))(req)
        } else {
            ServerResponse.status(HttpStatus.FORBIDDEN).body(fromObject("no entry for you"))
        }
    }
}


