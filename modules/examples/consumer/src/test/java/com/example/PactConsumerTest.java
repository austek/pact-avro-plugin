package com.example;

import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Interaction;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.google.protobuf.InvalidProtocolBufferException;
import io.pact.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "avro-provider", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V4)
class PactConsumerTest {
    String schemasPath = Objects.requireNonNull(getClass().getResource("/schemas.avsc")).getPath();

    @Pact(consumer = "avro-consumer")
    V4Pact configureInteractionResponseMessage(PactBuilder builder) {
        return builder
                .usingPlugin("avro")
                .expectsToReceive("Configure Interaction Response", "core/interaction/message")
                .with(Map.of(
                        "message.contents", Map.of(
                                "pact:avro", schemasPath,
                                "pact:record-name", "Item",
                                "pact:content-type", "avro/binary",
                                "name", "notEmpty('Item-41')",
                                "id", "notEmpty('41')"
                        )
                ))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "configureInteractionResponseMessage")
    void consumeConfigureInteractionResponseMessage(V4Interaction.AsynchronousMessage message) throws InvalidProtocolBufferException {
        Plugin.InteractionResponse response = Plugin.InteractionResponse.parseFrom(message.getContents().getContents().getValue());
        assertThat(response.getContents().getContentType(), is("avro/binary"));
        assertThat(response.getContents().getContent().getValue().toStringUtf8(), is("{}"));
        assertThat(response.getContents().getContentTypeHint(), is(Plugin.Body.ContentTypeHint.BINARY));

        assertThat(response.getRulesCount(), is(1));
        Map<String, Plugin.MatchingRules> rulesMap = response.getRulesMap();
        assertThat(rulesMap.keySet().iterator().next(), is("$.test.one"));
        Plugin.MatchingRules matchingRules = rulesMap.get("$.test.one");
        assertThat(matchingRules.getRuleCount(), is(1));
        assertThat(matchingRules.getRule(0).getType(), is("regex"));

        assertThat(response.getGeneratorsCount(), is(2));
        Map<String, Plugin.Generator> generatorsMap = response.getGeneratorsMap();
        assertThat(generatorsMap.keySet(), is(equalTo(Set.of("$.test.one", "$.test.two"))));
        assertThat(generatorsMap.get("$.test.one").getType(), is(equalTo("DateTime")));
        assertThat(generatorsMap.get("$.test.one").getValues().getFieldsMap().get("format").getStringValue(), is(equalTo("YYYY-MM-DD")));
    }
}
