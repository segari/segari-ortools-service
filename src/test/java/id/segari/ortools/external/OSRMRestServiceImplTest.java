package id.segari.ortools.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OSRM emits {@code null} for pairs it cannot route. Those cells must never reach the solver as 0:
 * a zero-length arc is the cheapest one available and would actively attract the solver to exactly
 * the pairs that are unroutable.
 */
class OSRMRestServiceImplTest {

    private static final List<LatLong> LOCATIONS = List.of(
            new LatLong(0.0, 0.0),                              // DUMMY
            new LatLong(-6.19742022712038, 106.898010979866),   // SP
            new LatLong(-6.1726244, 106.9036136),               // order 16043557
            new LatLong(-6.23029067083391, 106.934339180589));  // order 16044090

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://osrm.test");
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    }

    private OSRMRestService serviceRespondingWith(String body) {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://osrm.test/table/v1/driving/")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return new OSRMRestServiceImpl(builder.build());
    }

    @Test
    @DisplayName("a null cell becomes UNREACHABLE, not 0")
    void nullCellBecomesUnreachable() {
        String body = """
                {"code":"Ok",
                 "distances":[[0,73588,73721.4,78611],
                              [72014.6,0,5812.2,8666.9],
                              [71801.9,5508.4,0,null],
                              [80183.7,8718.3,null,0]],
                 "durations":[[0,3454.6,3528,3755.6],
                              [3364.9,0,424.3,676.3],
                              [3477.5,410.1,0,709.4],
                              [3679.9,643.8,723,0]]}
                """;

        OSRMTableResponseDTO response = serviceRespondingWith(body).getMatrix(LOCATIONS);

        assertThat(response.distances()[2][3]).isEqualTo(OSRMRestService.UNREACHABLE);
        assertThat(response.distances()[3][2]).isEqualTo(OSRMRestService.UNREACHABLE);
        // untouched cells still round normally
        assertThat(response.distances()[1][2]).isEqualTo(5812L);
        assertThat(response.distances()[1][3]).isEqualTo(8667L);
        assertThat(response.durations()[2][3]).isEqualTo(709L);
    }

    @Test
    @DisplayName("a null duration cell becomes UNREACHABLE too")
    void nullDurationCellBecomesUnreachable() {
        String body = """
                {"code":"Ok",
                 "distances":[[0,1],[1,0]],
                 "durations":[[0,null],[null,0]]}
                """;

        OSRMTableResponseDTO response = serviceRespondingWith(body).getMatrix(LOCATIONS);

        assertThat(response.durations()[0][1]).isEqualTo(OSRMRestService.UNREACHABLE);
        assertThat(response.durations()[1][0]).isEqualTo(OSRMRestService.UNREACHABLE);
        assertThat(response.distances()[0][1]).isEqualTo(1L);
    }

    @Test
    @DisplayName("a fully null row is filled with UNREACHABLE instead of throwing")
    void nullRowIsFilledWithUnreachable() {
        String body = """
                {"code":"Ok",
                 "distances":[[0,5812.2,8666.9],
                              null,
                              [8718.3,9833,0]],
                 "durations":[[0,424.3,676.3],
                              [410.1,0,709.4],
                              [643.8,723,0]]}
                """;

        OSRMTableResponseDTO response = serviceRespondingWith(body).getMatrix(LOCATIONS);

        assertThat(response.distances()[1]).containsOnly(OSRMRestService.UNREACHABLE);
        assertThat(response.distances()[0][1]).isEqualTo(5812L);
    }

    @Test
    @DisplayName("a real zero survives conversion untouched")
    void realZeroIsPreserved() {
        String body = """
                {"code":"Ok",
                 "distances":[[0,0],[0,0]],
                 "durations":[[0,0],[0,0]]}
                """;

        OSRMTableResponseDTO response = serviceRespondingWith(body).getMatrix(LOCATIONS);

        assertThat(response.distances()[0][1]).isZero();
    }

    @Test
    @DisplayName("UNREACHABLE stays clear of overflow once overhead and slack are added")
    void unreachableCannotOverflow() {
        assertThat(OSRMRestService.UNREACHABLE).isLessThan(Long.MAX_VALUE / 4);
        assertThat(OSRMRestService.UNREACHABLE).isGreaterThan(1_000_000L);
    }
}
