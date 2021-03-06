package info.jdavid.ok.server;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import info.jdavid.ok.server.samples.SSEServer;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;


//@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SuppressWarnings("ConstantConditions")
public class SSEServerTest {

  private static Request.Builder request(@Nullable final String... segments) {
    HttpUrl.Builder url = new HttpUrl.Builder().scheme("http").host("localhost").port(8082);
    if (segments != null) {
      for (final String segment: segments) {
        url.addPathSegment(segment);
      }
    }
    return new Request.Builder().url(url.build());
  }

  private static final OkHttpClient client = new OkHttpClient();

  private static OkHttpClient client() {
    return client.newBuilder().readTimeout(10, TimeUnit.SECONDS).build();
  }

  private static final SSEServer SERVER = new SSEServer(8082, 5, 3, 3);

  @BeforeClass
  public static void startServer() {
    SERVER.start();
    // Use an http client once to get rid of the static initializer penalty.
    // This is done so that the first test elapsed time doesn't get artificially high.
    try {
      final OkHttpClient c = client.newBuilder().readTimeout(1, TimeUnit.SECONDS).build();
      c.newCall(new Request.Builder().url("http://google.com").build()).execute();
    }
    catch (final IOException ignore) {}
  }

  @AfterClass
  public static void stopServer() {
    SERVER.stop();
  }

  @Test
  public void testStream() throws IOException {
    final Response r = client().newCall(
      request("sse").
        build()
    ).execute();
    assertEquals(Protocol.HTTP_1_1, r.protocol());
    assertEquals(200, r.code());
    assertEquals("OK", r.message());
    assertEquals("keep-alive", r.header("Connection"));
    final String contentLengthHeader = r.header("Content-Length");
    assertTrue(contentLengthHeader == null || "-1".equals(contentLengthHeader));
    final Buffer buffer = new Buffer();
    final BufferedSource source = r.body().source();
    while (!source.exhausted()) {
      source.readAll(buffer);
    }
    assertEquals("retry: 5", buffer.readUtf8Line());
    assertTrue(source.exhausted());
    for (int i=0; i<5; ++i) {
      int count = 0;
      while (buffer.size() < 10 && ++count < 5) {
        source.readAll(buffer);
        try { Thread.sleep(1000L); } catch (final InterruptedException ignore) {}
      }
      assertTrue(buffer.size() >= 10);
      assertEquals("data: OK", buffer.readUtf8Line());
      assertEquals("", buffer.readUtf8Line());
    }
    assertTrue(buffer.exhausted());
    assertTrue(source.exhausted());
  }

}
