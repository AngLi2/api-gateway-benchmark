package apigateway.benchmark.demo.edgeservice;

import org.apache.servicecomb.foundation.common.utils.BeanUtils;
import org.apache.servicecomb.foundation.common.utils.Log4jUtils;
import org.apache.servicecomb.serviceregistry.RegistryUtils;

import java.util.Collections;

public class EdgeMain {
    public static void main(String[] args) throws Exception{
        System.setProperty("local.registry.file","notExistJustForForceLocal");
        Log4jUtils.init();
        BeanUtils.init();
        String endpoints="rest://127.0.0.1:8081";
        RegistryUtils.getServiceRegistry().registerMicroserviceMappingByEndpoints(
                "thirdPartyService",
                "0.0.1",
                Collections.singletonList(endpoints),
                Service.class
        );
    }
}
