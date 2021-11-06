package data.shows;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.EpisodeSimplified;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.requests.data.shows.GetShowsEpisodesRequest;
import org.apache.hc.core5.http.ParseException;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class GetShowsEpisodesExample {
  private static final String accessToken = "taHZ2SdB-bPA3FsK3D7ZN5npZS47cMy-IEySVEGttOhXmqaVAIo0ESvTCLjLBifhHOHOIuhFUKPW1WMDP7w6dj3MAZdWT8CLI2MkZaXbYLTeoDvXesf2eeiLYPBGdx8tIwQJKgV8XdnzH_DONk";
  private static final String id = "5AvwZVawapvyhJUIx71pdJ";

  private static final SpotifyApi spotifyApi = new SpotifyApi.Builder()
    .setAccessToken(accessToken)
    .build();
  private static final GetShowsEpisodesRequest getShowsEpisodesRequest = spotifyApi.getShowEpisodes(id)
//          .limit(10)
//          .offset(0)
//          .market(CountryCode.SE)
    .build();

  public static void getShowsEpisodes_Sync() {
    try {
      final Paging<EpisodeSimplified> episodeSimplifiedPaging = getShowsEpisodesRequest.execute();

      System.out.println("Total: " + episodeSimplifiedPaging.getTotal());
    } catch (IOException | SpotifyWebApiException | ParseException e) {
      System.out.println("Error: " + e.getMessage());
    }
  }

  public static void getShowsEpisodes_Async() {
    try {
      final CompletableFuture<Paging<EpisodeSimplified>> pagingFuture = getShowsEpisodesRequest.executeAsync();

      // Thread free to do other tasks...

      // Example Only. Never block in production code.
      final Paging<EpisodeSimplified> episodeSimplifiedPaging = pagingFuture.join();

      System.out.println("Total: " + episodeSimplifiedPaging.getTotal());
    } catch (CompletionException e) {
      System.out.println("Error: " + e.getCause().getMessage());
    } catch (CancellationException e) {
      System.out.println("Async operation cancelled.");
    }
  }

  public static void main(String[] args) {
    getShowsEpisodes_Sync();
    getShowsEpisodes_Async();
  }
}
