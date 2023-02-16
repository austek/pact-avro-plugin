package com.example;

import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.ContentTypeHint;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Interaction;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.matchingrules.MatchingRule;
import au.com.dius.pact.core.model.matchingrules.MatchingRuleCategory;
import au.com.dius.pact.core.model.matchingrules.MatchingRuleGroup;
import au.com.dius.pact.core.model.matchingrules.MatchingRulesImpl;
import au.com.dius.pact.core.model.v4.MessageContents;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

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
                                "id", "notEmpty('100')"
                        )
                ))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "configureInteractionResponseMessage")
    void consumeConfigureInteractionResponseMessage(V4Interaction.AsynchronousMessage message) throws IOException {
        MessageContents messageContents = message.getContents();
        List<Item> items = arrayByteToAvroRecord(Item.class, messageContents.getContents().getValue());
        CharSequence name = "Item-41";
        assertThat(items).hasSize(1);
        Item item = items.get(0);
        assertThat(item.getName()).hasToString("Item-41");
        assertThat(item.getId()).isEqualTo(100L);

        assertThat(messageContents.getContents().getContentType()).hasToString("avro/binary; record=Item");
        assertThat(messageContents.getContents().getContentTypeHint()).isEqualTo(ContentTypeHint.BINARY);

        Map<String, MatchingRuleCategory> ruleCategoryMap = ((MatchingRulesImpl) messageContents.getMatchingRules()).getRules();
        assertThat(ruleCategoryMap).hasSize(1);
        Map<String, MatchingRuleGroup> rules = ruleCategoryMap.get("body").getMatchingRules();
        List<MatchingRule> nameRules = rules.get("$.name").getRules();
        assertThat(nameRules).hasSize(1);
        assertThat(nameRules.get(0)).extracting("name").isEqualTo("not-empty");

//        Map<String, Plugin.MatchingRules> rulesMap = response.getRulesMap();
//        assertThat(rulesMap.keySet().iterator().next(), is("$.test.one"));
//        Plugin.MatchingRules matchingRules = rulesMap.get("$.test.one");
//        assertThat(matchingRules.getRuleCount(), is(1));
//        assertThat(matchingRules.getRule(0).getType(), is("regex"));
    }

    private <T> List<T> arrayByteToAvroRecord(Class<T> c, byte[] bytes) throws IOException {
        SpecificDatumReader<T> datumReader = new SpecificDatumReader<T>(c);
        List<T> records = new ArrayList<>();

        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(in, null);
            while (!decoder.isEnd()) records.add(datumReader.read(null, decoder));
        }

        return records;
    }
}
