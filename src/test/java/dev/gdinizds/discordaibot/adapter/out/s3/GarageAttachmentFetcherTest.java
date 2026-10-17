package dev.gdinizds.discordaibot.adapter.out.s3;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GarageAttachmentFetcherTest {

    @Test
    void extractsBucketAndKeyFromTheGatewayUrl() {
        var location = GarageAttachmentFetcher.parse(
                "http://garage.garage.svc.cluster.local:3900/discord-gateway-attachments/900/600/print.png");

        assertThat(location.bucket()).isEqualTo("discord-gateway-attachments");
        assertThat(location.key()).isEqualTo("900/600/print.png");
    }

    @Test
    void acceptsRawAndEncodedFileNames() {
        assertThat(GarageAttachmentFetcher.parse("http://g:3900/b/1/2/meu arquivo.txt").key()).isEqualTo("1/2/meu arquivo.txt");
        assertThat(GarageAttachmentFetcher.parse("http://g:3900/b/1/2/meu%20arquivo.txt").key()).isEqualTo("1/2/meu arquivo.txt");
    }

    @Test
    void rejectsUrlWithoutKey() {
        assertThatThrownBy(() -> GarageAttachmentFetcher.parse("http://g:3900/bucket")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAttachmentFromDifferentBucket() {
        var fetcher = new GarageAttachmentFetcher(null, null, "expected-bucket", 1024);
        var att = new dev.gdinizds.discordaibot.domain.model.InputAttachment(
                "http://g:3900/other-bucket/secret.txt", "secret.txt", dev.gdinizds.discordaibot.domain.model.MediaKind.TEXT);

        assertThatThrownBy(() -> fetcher.fetch(att))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acesso negado ao bucket: other-bucket");
    }
}

