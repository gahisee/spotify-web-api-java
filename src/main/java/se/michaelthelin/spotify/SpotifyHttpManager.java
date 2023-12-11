package se.michaelthelin.spotify;

import com.google.gson.*;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.CachingHttpClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.exceptions.detailed.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;

public class SpotifyHttpManager implements IHttpManager {

  private static final int DEFAULT_CACHE_MAX_ENTRIES = 1000;
  private static final int DEFAULT_CACHE_MAX_OBJECT_SIZE = 8192;
  private static final Gson GSON = new Gson();
  private final CloseableHttpClient httpClient;
  private final CloseableHttpClient httpClientCaching;
  private final HttpHost proxy;
  private final UsernamePasswordCredentials proxyCredentials;
  private final Integer cacheMaxEntries;
  private final Integer cacheMaxObjectSize;
  private final Integer connectionRequestTimeout;
  private final Integer connectTimeout;
  private final Integer socketTimeout;
  private final boolean usePooledConnectionManager;

  /**
   * Construct a new SpotifyHttpManager instance.
   *
   * @param builder The builder.
   */
  public SpotifyHttpManager(Builder builder) {
    this.proxy = builder.proxy;
    this.proxyCredentials = builder.proxyCredentials;
    this.cacheMaxEntries = builder.cacheMaxEntries;
    this.cacheMaxObjectSize = builder.cacheMaxObjectSize;
    this.connectionRequestTimeout = builder.connectionRequestTimeout;
    this.connectTimeout = builder.connectTimeout;
    this.socketTimeout = builder.socketTimeout;
    this.usePooledConnectionManager = builder.usePoolingConnectionManager;
    HttpClientConnectionManager connectionManager = null;

    CacheConfig cacheConfig = CacheConfig.custom()
      .setMaxCacheEntries(cacheMaxEntries != null ? cacheMaxEntries : DEFAULT_CACHE_MAX_ENTRIES)
      .setMaxObjectSize(cacheMaxObjectSize != null ? cacheMaxObjectSize : DEFAULT_CACHE_MAX_OBJECT_SIZE)
      .setSharedCache(false)
      .build();

    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();

    if (proxy != null) {
      credentialsProvider.setCredentials(
        new AuthScope(null, proxy.getHostName(), proxy.getPort(), null, proxy.getSchemeName()),
        proxyCredentials
      );
    }

    ConnectionConfig connectionConfig = ConnectionConfig
      .custom()
      .setConnectTimeout(builder.connectTimeout != null
        ? Timeout.ofMilliseconds(builder.connectTimeout)
        : ConnectionConfig.DEFAULT.getConnectTimeout())
      .build();

      if (usePooledConnectionManager)
      {
        connectionManager = new PoolingHttpClientConnectionManager();
        SpotifyApi.LOGGER.log(
          Level.FINE,
          "Using PoolingHttpClientConnectionManager.");
      }
      else
      {
        connectionManager = new BasicHttpClientConnectionManager();
        ((BasicHttpClientConnectionManager)connectionManager).setConnectionConfig(connectionConfig);
        SpotifyApi.LOGGER.log(
          Level.FINE,
          "Using BasicHttpClientConnectionManager.");
      }
    
      RequestConfig requestConfig = RequestConfig
      .custom()
      .setCookieSpec(StandardCookieSpec.STRICT)
      .setConnectionRequestTimeout(builder.connectionRequestTimeout != null
        ? Timeout.ofMilliseconds(builder.connectionRequestTimeout)
        : RequestConfig.DEFAULT.getConnectionRequestTimeout())
      .setResponseTimeout(builder.socketTimeout != null
        ? Timeout.ofMilliseconds(builder.socketTimeout)
        : RequestConfig.DEFAULT.getResponseTimeout())
      .build();
    HttpRequestRetryStrategy retryStrategy = new SpotifyHttpRequestRetryStrategy();

    this.httpClient = HttpClients
      .custom()
      .disableContentCompression()
      .setConnectionManager(connectionManager)
      .setDefaultCredentialsProvider(credentialsProvider)
      .setDefaultRequestConfig(requestConfig)
      .setProxy(proxy)
      .setRetryStrategy(retryStrategy)
      .build();

    this.httpClientCaching = CachingHttpClients
      .custom()
      .setCacheConfig(cacheConfig)
      .disableContentCompression()
      .setConnectionManager(connectionManager)
      .setDefaultCredentialsProvider(credentialsProvider)
      .setDefaultRequestConfig(requestConfig)
      .setProxy(proxy)
      .setRetryStrategy(retryStrategy)
      .build();
  }

  public static URI makeUri(String uriString) {
    try {
      return new URI(uriString);
    } catch (URISyntaxException e) {
      SpotifyApi.LOGGER.log(
        Level.SEVERE,
        "URI Syntax Exception for \"" + uriString + "\"");
      return null;
    }
  }

  public HttpHost getProxy() {
    return proxy;
  }

  public UsernamePasswordCredentials getProxyCredentials() {
    return proxyCredentials;
  }

  public Integer getCacheMaxEntries() {
    return cacheMaxEntries;
  }

  public Integer getCacheMaxObjectSize() {
    return cacheMaxObjectSize;
  }

  public Integer getConnectionRequestTimeout() {
    return connectionRequestTimeout;
  }

  public Integer getConnectTimeout() {
    return connectTimeout;
  }

  public Integer getSocketTimeout() {
    return socketTimeout;
  }

  @Override
  public String get(URI uri, Header[] headers) throws
    IOException,
    SpotifyWebApiException,
    ParseException {
    assert (uri != null);
    assert (!uri.toString().isEmpty());

    final HttpGet httpGet = new HttpGet(uri);

    httpGet.setHeaders(headers);
    SpotifyApi.LOGGER.log(
      Level.FINE,
      "GET request uses these headers: " + GSON.toJson(headers));

    String responseBody = getResponseBody(execute(httpClientCaching, httpGet));

    httpGet.reset();

    return responseBody;
  }

  @Override
  public String post(URI uri, Header[] headers, HttpEntity body) throws
    IOException,
    SpotifyWebApiException,
    ParseException {
    assert (uri != null);
    assert (!uri.toString().isEmpty());

    final HttpPost httpPost = new HttpPost(uri);

    httpPost.setHeaders(headers);
    httpPost.setEntity(body);
    SpotifyApi.LOGGER.log(
      Level.FINE,
      "POST request uses these headers: " + GSON.toJson(headers));

    String responseBody = getResponseBody(execute(httpClient, httpPost));

    httpPost.reset();

    return responseBody;
  }

  @Override
  public String put(URI uri, Header[] headers, HttpEntity body) throws
    IOException,
    SpotifyWebApiException,
    ParseException {
    assert (uri != null);
    assert (!uri.toString().isEmpty());

    final HttpPut httpPut = new HttpPut(uri);

    httpPut.setHeaders(headers);
    httpPut.setEntity(body);
    SpotifyApi.LOGGER.log(
      Level.FINE,
      "PUT request uses these headers: " + GSON.toJson(headers));

    String responseBody = getResponseBody(execute(httpClient, httpPut));

    httpPut.reset();

    return responseBody;
  }

  @Override
  public String delete(URI uri, Header[] headers, HttpEntity body) throws
    IOException,
    SpotifyWebApiException,
    ParseException {
    assert (uri != null);
    assert (!uri.toString().isEmpty());

    final HttpDelete httpDelete = new HttpDelete(uri);

    httpDelete.setHeaders(headers);
    httpDelete.setEntity(body);
    SpotifyApi.LOGGER.log(
      Level.FINE,
      "DELETE request uses these headers: " + GSON.toJson(headers));

    String responseBody = getResponseBody(execute(httpClient, httpDelete));

    httpDelete.reset();

    return responseBody;
  }

  private CloseableHttpResponse execute(CloseableHttpClient httpClient, ClassicHttpRequest method) throws
    IOException {
    HttpCacheContext context = HttpCacheContext.create();
    CloseableHttpResponse response = httpClient.execute(method, context);

    try {
      CacheResponseStatus responseStatus = context.getCacheResponseStatus();

      if (responseStatus != null) {
        switch (responseStatus) {
          case CACHE_HIT:
            SpotifyApi.LOGGER.log(
              Level.CONFIG,
              "A response was generated from the cache with no requests sent upstream");
            break;
          case CACHE_MODULE_RESPONSE:
            SpotifyApi.LOGGER.log(
              Level.CONFIG,
              "The response was generated directly by the caching module");
            break;
          case CACHE_MISS:
            SpotifyApi.LOGGER.log(
              Level.CONFIG,
              "The response came from an upstream server");
            break;
          case VALIDATED:
            SpotifyApi.LOGGER.log(
              Level.CONFIG,
              "The response was generated from the cache after validating the entry with the origin server");
            break;
          case FAILURE:
            SpotifyApi.LOGGER.log(
              Level.CONFIG,
              "The response came from an upstream server after a cache failure");
            break;
        }
      }
    } catch (Exception e) {
      SpotifyApi.LOGGER.log(Level.SEVERE, e.getMessage());
    }

    return response;
  }

  private String getResponseBody(CloseableHttpResponse httpResponse) throws
    IOException,
    SpotifyWebApiException,
    ParseException {

    final String responseBody = httpResponse.getEntity() != null
      ? EntityUtils.toString(httpResponse.getEntity(), "UTF-8")
      : null;
    String errorMessage = httpResponse.getReasonPhrase();

    SpotifyApi.LOGGER.log(
      Level.FINE,
      "The http response has body " + responseBody);

    if (responseBody != null && !responseBody.isEmpty()) {
      try {
        final JsonElement jsonElement = JsonParser.parseString(responseBody);

        if (jsonElement.isJsonObject()) {
          final JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();

          if (jsonObject.has("error")) {
            if (jsonObject.has("error_description")) {
              errorMessage = jsonObject.get("error_description").getAsString();
            } else if (jsonObject.get("error").isJsonObject() && jsonObject.getAsJsonObject("error").has("message")) {
              errorMessage = jsonObject.getAsJsonObject("error").get("message").getAsString();
            }
          }
        }
      } catch (JsonSyntaxException e) {
        // Not necessary
      }
    }

    SpotifyApi.LOGGER.log(
      Level.FINE,
      "The http response has status code " + httpResponse.getCode());

    switch (httpResponse.getCode()) {
      case HttpStatus.SC_BAD_REQUEST:
        throw new BadRequestException(errorMessage);
      case HttpStatus.SC_UNAUTHORIZED:
        throw new UnauthorizedException(errorMessage);
      case HttpStatus.SC_FORBIDDEN:
        throw new ForbiddenException(errorMessage);
      case HttpStatus.SC_NOT_FOUND:
        throw new NotFoundException(errorMessage);
      case 429: // TOO_MANY_REQUESTS (additional status code, RFC 6585)
        // Sets "Retry-After" header as described at https://beta.developer.spotify.com/documentation/web-api/#rate-limiting
        Header header = httpResponse.getFirstHeader("Retry-After");

        if (header != null) {
          throw new TooManyRequestsException(errorMessage, Integer.parseInt(header.getValue()));
        } else {
          throw new TooManyRequestsException(errorMessage);
        }
      case HttpStatus.SC_INTERNAL_SERVER_ERROR:
        throw new InternalServerErrorException(errorMessage);
      case HttpStatus.SC_BAD_GATEWAY:
        throw new BadGatewayException(errorMessage);
      case HttpStatus.SC_SERVICE_UNAVAILABLE:
        throw new ServiceUnavailableException(errorMessage);
      default:
        return responseBody;
    }
  }

  public static class Builder {
    private HttpHost proxy;
    private UsernamePasswordCredentials proxyCredentials;
    private Integer cacheMaxEntries;
    private Integer cacheMaxObjectSize;
    private Integer connectionRequestTimeout;
    private Integer connectTimeout;
    private Integer socketTimeout;
    private boolean usePoolingConnectionManager = false;

    public Builder setProxy(HttpHost proxy) {
      this.proxy = proxy;
      return this;
    }

    public Builder setProxyCredentials(UsernamePasswordCredentials proxyCredentials) {
      this.proxyCredentials = proxyCredentials;
      return this;
    }

    public Builder setCacheMaxEntries(Integer cacheMaxEntries) {
      this.cacheMaxEntries = cacheMaxEntries;
      return this;
    }

    public Builder setCacheMaxObjectSize(Integer cacheMaxObjectSize) {
      this.cacheMaxObjectSize = cacheMaxObjectSize;
      return this;
    }

    public Builder setConnectionRequestTimeout(Integer connectionRequestTimeout) {
      this.connectionRequestTimeout = connectionRequestTimeout;
      return this;
    }

    public Builder setConnectTimeout(Integer connectTimeout) {
      this.connectTimeout = connectTimeout;
      return this;
    }

    public Builder setSocketTimeout(Integer socketTimeout) {
      this.socketTimeout = socketTimeout;
      return this;
    }

    public Builder setPoolingConnectionManager() {
      this.usePoolingConnectionManager = true;
      return this;
    }
    
    public SpotifyHttpManager build() {
      return new SpotifyHttpManager(this);
    }
  }
}
