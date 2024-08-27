package com.github.austek.example.pulsar.avro;

import static com.github.austek.example.pulsar.avro.PactPulsarConsumerTest.arrayByteToAvroRecord;
import static org.assertj.core.api.Assertions.assertThat;

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
import com.collibra.event.client.examples.showcase.domain.OrderItem;
import com.collibra.event.client.examples.showcase.schema.OrderNewEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(
    pactVersion = PactSpecVersion.V4,
    providerType = ProviderType.ASYNCH,
    providerName = "OrderTopicV1")
class OrderV1ConsumerTest {
  private String schemasPath =
      Objects.requireNonNull(getClass().getResource("/avro/order-v1.avsc")).getPath();

  @Pact(consumer = "OrderTopicConsumer")
  V4Pact configureRecordWithDependantRecord(PactBuilder builder) {
    if (System.getProperty("os.name").toLowerCase().contains("win")
        && schemasPath.startsWith("/")) {
      schemasPath = schemasPath.substring(1);
    }
    ;
    var messageBody =
        Map.of(
            "message.contents",
            Map.ofEntries(
                Map.entry("pact:avro", schemasPath),
                Map.entry("pact:record-name", "OrderNewEvent"),
                Map.entry("pact:content-type", "avro/binary"),
                Map.entry("orderId", "notEmpty('0c7cbb5a-9a9a-4088-9713-c0c88475c903')"),
                Map.entry("userId", "notEmpty('20bef962-8cbd-4b8c-8337-97ae385ac45d')"),
                Map.entry(
                    "items",
                    List.of(
                        Map.of(
                            "itemId", "notEmpty('e41c5f30-fa8e-4cfd-989d-95ca5a04037f')",
                            "quantity", "notEmpty('1')"),
                        Map.of(
                            "itemId", "notEmpty('8a62474a-7157-4c67-9126-c6dcecb1df08')",
                            "quantity", "notEmpty('2')")))));
    return builder
        .usingPlugin("avro")
        .expectsToReceive("Order Created", "core/interaction/message")
        .with(messageBody)
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "configureRecordWithDependantRecord")
  void consumerRecordWithDependantRecord(V4Interaction.AsynchronousMessage message)
      throws IOException {
    MessageContents messageContents = message.getContents();
    List<OrderNewEvent> orders =
        arrayByteToAvroRecord(OrderNewEvent.class, messageContents.getContents().getValue());
    assertFirstOrder(orders);

    assertThat(messageContents.getContents().getContentType())
        .hasToString("avro/binary; record=OrderNewEvent");
    assertThat(messageContents.getContents().getContentTypeHint())
        .isEqualTo(ContentTypeHint.BINARY);

    Map<String, MatchingRuleCategory> ruleCategoryMap =
        ((MatchingRulesImpl) messageContents.getMatchingRules()).getRules();
    assertThat(ruleCategoryMap).hasSize(1);
    Map<String, MatchingRuleGroup> rules = ruleCategoryMap.get("body").getMatchingRules();
    List<MatchingRule> idRules = rules.get("$.userId").getRules();
    assertThat(idRules).hasSize(1);
    assertThat(idRules.get(0)).extracting("name").isEqualTo("not-empty");
  }

  private static void assertFirstOrder(List<OrderNewEvent> orders) {
    assertThat(orders).hasSize(1);
    OrderNewEvent order = orders.get(0);
    assertThat(order.getOrderId()).hasToString("0c7cbb5a-9a9a-4088-9713-c0c88475c903");
    assertThat(order.getUserId()).hasToString("20bef962-8cbd-4b8c-8337-97ae385ac45d");
    assertThat(order.getItems()).hasSize(2);
    OrderItem item1 = order.getItems().get(0);
    assertThat(item1.getItemId()).hasToString("e41c5f30-fa8e-4cfd-989d-95ca5a04037f");
    assertThat(item1.getQuantity()).isEqualTo(1);
    OrderItem item2 = order.getItems().get(1);
    assertThat(item2.getItemId()).hasToString("8a62474a-7157-4c67-9126-c6dcecb1df08");
    assertThat(item2.getQuantity()).isEqualTo(2L);
  }
}
