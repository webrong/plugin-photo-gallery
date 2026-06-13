package run.halo.gallery.endpoint.publicapi;

import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.QueryFactory;
import run.halo.gallery.group.AlbumGroup;
import run.halo.gallery.vo.AlbumGroupVo;

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
                builder -> builder.operationId("publicListAlbumGroups").tag(tag)
                    .description("公开列出相册分组"))
            .GET("/albumgroups/{name}", this::getGroup,
                builder -> builder.operationId("publicGetAlbumGroup").tag(tag)
                    .description("公开获取相册分组"))
            .build();
    }

    private Mono<ServerResponse> listGroups(ServerRequest request) {
        return client.listAll(AlbumGroup.class,
                ListOptions.builder()
                    .fieldQuery(QueryFactory.notEqual("spec.hideFromList", "true"))
                    .build(),
                org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.asc("spec.priority"),
                    org.springframework.data.domain.Sort.Order.desc("metadata.creationTimestamp")
                ))
            .map(AlbumGroupVo::from)
            .collectList()
            .flatMap(groups -> ServerResponse.ok().bodyValue(
                new PublicListResponse<>(groups)));
    }

    private Mono<ServerResponse> getGroup(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(AlbumGroup.class, name)
            .filter(g -> !g.getSpec().isHideFromList())
            .map(AlbumGroupVo::from)
            .flatMap(vo -> ServerResponse.ok().bodyValue(vo))
            .switchIfEmpty(Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "分组不存在或已隐藏")));
    }

    public record PublicListResponse<T>(List<T> items) {}
}
