package info.jdavid.ok.server.samples;

import java.util.concurrent.atomic.AtomicBoolean;

import com.squareup.okhttp.Headers;
import info.jdavid.ok.server.HttpServer;
import info.jdavid.ok.server.MediaTypes;
import info.jdavid.ok.server.Response;
import info.jdavid.ok.server.SSEBody;
import info.jdavid.ok.server.StatusLines;
import okio.Buffer;


public class SSEWithEventSource {

  protected final HttpServer mServer;
  private final int mRetry;
  private final int mPeriod;

  // Sends 5 OK messages.
  private class SSEEventSource extends SSEBody.DefaultEventSource {
    private final AtomicBoolean mStarted = new AtomicBoolean();
    private final Thread mThread = new Thread() {
      @Override public void run() {
        for (int i=0; i<4; ++i) {
          write("OK");
          try { Thread.sleep(mPeriod * 1000); } catch (final InterruptedException ignore) {}
        }
        end("OK");
      }
    };
    public void start() {
      if (mStarted.getAndSet(true)) throw new IllegalStateException();
      mThread.start();
    }
  }

  private final SSEEventSource mEventSource = new SSEEventSource();

  public SSEWithEventSource(final int port, final int retrySecs, final int periodSecs) {
    mRetry = retrySecs;
    mPeriod = periodSecs;
    //noinspection Duplicates
    mServer = new HttpServer() {
      @Override protected Response handle(final String method, final String path,
                                          final Headers requestHeaders, final Buffer requestBody) {
        if (!"GET".equals(method)) return unsupported();
        if (!"/sse".equals(path)) return notFound();
        return sse();
      }
    }.port(port);
  }

  private Response notFound() {
    return new Response.Builder().statusLine(StatusLines.NOT_FOUND).noBody().build();
  }

  private Response unsupported() {
    return new Response.Builder().statusLine(StatusLines.METHOD_NOT_ALLOWED).noBody().build();
  }

  private Response sse() {
    return new Response.Builder().
      statusLine(StatusLines.OK).
      header("Content-Type", MediaTypes.SSE.toString()).
      header("Cache-Control", "no-cache").
      header("Connection", "keep-alive").
      header("Access-Control-Allow-Origin", "*").
      header("Access-Control-Allow-Methods", "GET").
      header("Access-Control-Allow-Headers", "Content-Type, Accept").
      body(new SSEBody(mRetry, mEventSource)).build();
  }

  public void startLoop() {
    mEventSource.start();
  }

  public void start() {
    mServer.start();
  }

  @SuppressWarnings("unused")
  public void stop() {
    mServer.shutdown();
  }


  public static void main(final String[] args) {
    final HttpServer server = new HttpServer().port(8083);
    server.start();
    server.shutdown();
  }


  public static void main2(final String[] args) {
    new SSEWithEventSource(8080, 5, 10).start();
  }

}