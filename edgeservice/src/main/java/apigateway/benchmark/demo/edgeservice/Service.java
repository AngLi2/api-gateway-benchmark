package apigateway.benchmark.demo.edgeservice;

import io.swagger.annotations.Api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

@Path("/checked-out")
@Api(produces = MediaType.TEXT_PLAIN)
public interface Service {
    @GET
    String getCheckedOut();
}
