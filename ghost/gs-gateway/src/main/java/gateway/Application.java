package gateway;

import java.net.URI;
import java.time.Duration;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

// tag::code[]
@SpringBootApplication
@EnableConfigurationProperties(UriConfiguration.class)
@RestController
public class Application {

	private Logger logger = LoggerFactory.getLogger(Application.class);

	@Value("${ghost.endpoint}")
	private String ghostEndpoint;

	@Value("${matomo.host}")
	private String matomoHost;

	@Value("${matomo.port}")
	private String matomoPort;


	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public WebClient webClient() {

		ConnectionProvider provider = ConnectionProvider.builder("fixed")
				.maxConnections(500)
				.maxIdleTime(Duration
						.ofSeconds(20))
				.maxLifeTime(Duration.ofSeconds(60))
				.pendingAcquireTimeout(Duration.ofSeconds(60))
				.evictInBackground(Duration.ofSeconds(120)).build();

		return WebClient.builder()
				.clientConnector(new ReactorClientHttpConnector(HttpClient.create(provider).responseTimeout(Duration.ofMillis(600))))
				.build();
	}

	// tag::route-locator[]
	@Bean
	public RouteLocator myRoutes(RouteLocatorBuilder builder, UriConfiguration uriConfiguration, WebClient webClient) {
		String httpUri = uriConfiguration.getHttpbin();
		return builder.routes()
			.route(p -> p
				.path("/**")
				.filters(f -> f.preserveHostHeader().filter(new GatewayFilter() {

					@Override
					public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
						return chain.filter(exchange).doOnSuccess(f->{sendTracking(exchange);});
					}
					public void sendTracking(ServerWebExchange exchange) {
						logger.info("send tracking {}",exchange.getResponse().getHeaders().getContentType());
						if(exchange.getResponse().getHeaders().getContentType()!= null && exchange.getResponse().getHeaders().getContentType().toString().startsWith("text/html")) {
							webClient.get().uri(uriBuilder -> {
								uriBuilder = uriBuilder.scheme("http").host(matomoHost).port(Integer.parseInt(matomoPort))
										.path("//matomo.php").queryParam("idsite", "1").queryParam("rec", "1")
										.queryParam("action_name", "pageView").queryParam("rand", new Random().nextInt())
//										.queryParam("apiv", "1").queryParam("token_auth", token)
//										.queryParam("cip", (String) exchange.getRequest().getHeaders().getFirst("x-real-ip"))
//										.queryParam("city", (String) exchange.getRequest().getHeaders().getFirst("X-geoip-city"))
//										.queryParam("lang", "en")
//										.queryParam("region", (String) exchange.getRequest().getHeaders().getFirst("X-geoip-region"))
//
//										.queryParam("lat", (String) exchange.getRequest().getHeaders().getFirst("X-geoip-latitude"))
//										.queryParam("long", (String) exchange.getRequest().getHeaders().getFirst("X-geoip-longitude"))
//										.queryParam("country", (String) exchange.getRequest().getHeaders().getFirst("X-geoip-city-country-code"))
//										.queryParam("urlref", exchange.getRequest().getHeaders().getFirst(HttpHeaders.REFERER))
										.queryParam("url",
												exchange.getRequest().getURI().toString().replaceAll("portal.app.svc.cluster.local",
														"www.findi.io"))
										.queryParam("ua", exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT));
								URI uri = uriBuilder.build();
								logger.info(uri.toString());
								return uri;

							}).retrieve().toEntity(String.class).doOnError(e->logger.warn(e.getMessage())).onErrorReturn(ResponseEntity.ok("")).subscribe();

						}

					}
				}))
				.uri(ghostEndpoint))
			.build();
	}
	// end::route-locator[]

	// tag::fallback[]
	@RequestMapping("/fallback")
	public Mono<String> fallback() {
		return Mono.just("fallback");
	}
	// end::fallback[]
}

// tag::uri-configuration[]
@ConfigurationProperties
class UriConfiguration {

	private String httpbin = "http://httpbin.org:80";

	public String getHttpbin() {
		return httpbin;
	}

	public void setHttpbin(String httpbin) {
		this.httpbin = httpbin;
	}
}
// end::uri-configuration[]
// end::code[]
