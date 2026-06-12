package run.halo.gallery.vo;

import lombok.Builder;
import lombok.Data;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.gallery.group.AlbumGroup;

@Data
@Builder
public class AlbumGroupVo implements ExtensionVoOperator {

    private final MetadataOperator metadata;
    private final AlbumGroup.AlbumGroupSpec spec;
    private final AlbumGroup.AlbumGroupStatus status;
    private final Integer albumCount;

    public static AlbumGroupVo from(AlbumGroup group) {
        return AlbumGroupVo.builder()
            .metadata(group.getMetadata())
            .spec(group.getSpec())
            .status(group.getStatus())
            .build();
    }

    public static AlbumGroupVo from(AlbumGroup group, Integer albumCount) {
        return AlbumGroupVo.builder()
            .metadata(group.getMetadata())
            .spec(group.getSpec())
            .status(group.getStatus())
            .albumCount(albumCount)
            .build();
    }
}
