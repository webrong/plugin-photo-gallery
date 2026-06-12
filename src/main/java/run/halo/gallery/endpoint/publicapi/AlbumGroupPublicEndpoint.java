package run.halo.gallery.endpoint.publicapi;

import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.gallery.group.AlbumGroup;

@Component
@RequiredArgsConstructor
public class AlbumGroupPublicEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.gallery.halo.run/v1alpha1");
    }

    @Override
    public org.springframework.web.reactive.function.server.RouterFunction<ServerResponse> endpoint() {
        var tag = "AlbumGroupPublicV1alpha1";
        return route()
            .GET("/albumgroups", this::listGroups,
                builder -> builder.operationId("publicListAlbumGroups").tag(tag).description("公开列出相册分组"))
            .GET("/albumgroups/{name}", this::getGroup,
                builder -> builder.operationId("publicGetAlbumGroup").tag(tag).description("公开获取相册分组"))
            .build();
    }

    private Mono<ServerResponse> listGroups(ServerRequest request) {
        return client.listAll(AlbumGroup.class,
                ListOptions.builder()
                    .fieldQuery(QueryFactory.notEqual("spec.hideFromList", "true"))
                    .build(),
                org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.asc("spec.priority")))
            .collectList()
            .flatMap(groups -> ServerResponse.ok().bodyValue(groups));
    }

    private Mono<ServerResponse> getGroup(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(AlbumGroup.class, name)
            .flatMap(group -> ServerResponse.ok().bodyValue(group))
            .switchIfEmpty(Mono.error(new RuntimeException("分组不存在")));
    }
}
