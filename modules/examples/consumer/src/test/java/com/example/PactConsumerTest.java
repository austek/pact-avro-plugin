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

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "avro-provider", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V4)
class PactConsumerTest {
    String schemasPath = Objects.requireNonNull(getClass().getResource("/schemas.avsc")).getPath();

    @Pact(consumer = "avro-consumer")
    V4Pact configureItemRecord(PactBuilder builder) {
        return builder
                .usingPlugin("avro")
                .expectsToReceive("Configure Single Record", "core/interaction/message")
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
    @PactTestFor(pactMethod = "configureItemRecord")
    void consumeSingleRecord(V4Interaction.AsynchronousMessage message) throws IOException {
        MessageContents messageContents = message.getContents();
        List<Item> items = arrayByteToAvroRecord(Item.class, messageContents.getContents().getValue());
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
        List<MatchingRule> idRules = rules.get("$.id").getRules();
        assertThat(idRules).hasSize(1);
        assertThat(idRules.get(0)).extracting("name").isEqualTo("not-empty");
    }


    @Pact(consumer = "avro-consumer")
    V4Pact configureRecordWithDependantRecord(PactBuilder builder) {
        return builder
                .usingPlugin("avro")
                .expectsToReceive("Configure multi records", "core/interaction/message")
                .with(Map.of(
                        "message.contents", Map.of(
                                "pact:avro", schemasPath,
                                "pact:record-name", "Complex",
                                "pact:content-type", "avro/binary",
                                "id", "notEmpty('100')",
                                "names", List.of(
                                        "notEmpty('name-1')",
                                        "notEmpty('name-2')"
                                ),
                                "enabled", "matching(boolean, true)",
                                "no", "matching(integer, 121)",
                                "height", "matching(decimal, 15.8)",
                                "width", "matching(decimal, 1.8)",
                                "ages", Map.of(
                                        "first", "matching(integer, 2)",
                                        "second", "matching(integer, 3)"
                                )
                        )
                ))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "configureRecordWithDependantRecord")
    void consumerRecordWithDependantRecord(V4Interaction.AsynchronousMessage message) throws IOException {
        MessageContents messageContents = message.getContents();
        List<Complex> complexes = arrayByteToAvroRecord(Complex.class, messageContents.getContents().getValue());
        assertThat(complexes).hasSize(1);
        Complex complex = complexes.get(0);
        assertThat(complex.getId()).isEqualTo(100);
        assertThat(complex.getNames()).hasSize(2);
        assertThat(complex.getNames().get(0)).hasToString("name-1");
        assertThat(complex.getNames().get(1)).hasToString("name-2");

        assertThat(messageContents.getContents().getContentType()).hasToString("avro/binary; record=Complex");
        assertThat(messageContents.getContents().getContentTypeHint()).isEqualTo(ContentTypeHint.BINARY);

        Map<String, MatchingRuleCategory> ruleCategoryMap = ((MatchingRulesImpl) messageContents.getMatchingRules()).getRules();
        assertThat(ruleCategoryMap).hasSize(1);
        Map<String, MatchingRuleGroup> rules = ruleCategoryMap.get("body").getMatchingRules();
        List<MatchingRule> idRules = rules.get("$.id").getRules();
        assertThat(idRules).hasSize(1);
        assertThat(idRules.get(0)).extracting("name").isEqualTo("not-empty");
        List<MatchingRule> nameRules = rules.get("$.names").getRules();
        assertThat(nameRules).hasSize(2);
        assertThat(nameRules.get(0)).extracting("name").isEqualTo("not-empty");
        assertThat(nameRules.get(1)).extracting("name").isEqualTo("not-empty");
    }

    private <T> List<T> arrayByteToAvroRecord(Class<T> c, byte[] bytes) throws IOException {
        SpecificDatumReader<T> datumReader = new SpecificDatumReader<>(c);
        List<T> records = new ArrayList<>();

        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(in, null);
            while (!decoder.isEnd()) records.add(datumReader.read(null, decoder));
        }

        return records;
    }
}
