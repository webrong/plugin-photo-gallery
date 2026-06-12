package run.halo.gallery.group;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.Nullable;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "gallery.halo.run", version = "v1alpha1",
    kind = "AlbumGroup", plural = "albumgroups", singular = "albumgroup")
public class AlbumGroup extends AbstractExtension {

    public static final String KIND = "AlbumGroup";

    @Schema(requiredMode = REQUIRED)
    private AlbumGroupSpec spec;

    @Schema
    private AlbumGroupStatus status;

    @Data
    public static class AlbumGroupSpec {
        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String displayName;

        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String slug;

        private String description;

        private String cover;

        @Schema(defaultValue = "0")
        private Integer priority;

        private @Nullable List<String> children;

        @Schema(defaultValue = "false")
        private boolean hideFromList;
    }

    @Data
    public static class AlbumGroupStatus {
        private String permalink;
        private Integer albumCount;
    }
}
