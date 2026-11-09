package com.payment.async.infra.persistance.out;

import org.springframework.http.HttpStatusCode;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AuthorizationClient {

    private final RestClient restClient = RestClient.create();

    public boolean get(){
        try {
            AuthorizationClientResponseDto dto = restClient.get()
                    .uri("https://util.devi.tools/api/v2/authorize")
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, ((request, response) -> {
                    }))
                    .body(AuthorizationClientResponseDto.class);

            if (dto == null || dto.data() == null){
                throw new RuntimeException();
            }

            return dto.data().authorization();

        } catch (RestClientResponseException e){
            throw new RuntimeException(e.getMessage());
    }
};
}
