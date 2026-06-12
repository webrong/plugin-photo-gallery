package run.halo.gallery.vo;

import lombok.Builder;
import lombok.Data;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.gallery.album.Album;

@Data
@Builder
public class AlbumVo implements ExtensionVoOperator {

    private final MetadataOperator metadata;
    private final Album.AlbumSpec spec;
    private final Album.AlbumStatus status;
    private final Integer photoCount;

    public static AlbumVo from(Album album) {
        return AlbumVo.builder()
            .metadata(album.getMetadata())
            .spec(album.getSpec())
            .status(album.getStatus())
            .build();
    }

    public static AlbumVo from(Album album, Integer photoCount) {
        return AlbumVo.builder()
            .metadata(album.getMetadata())
            .spec(album.getSpec())
            .status(album.getStatus())
            .photoCount(photoCount)
            .build();
    }
}
