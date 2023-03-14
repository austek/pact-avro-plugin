package com.collibra.example.pulsar.avro;

import au.com.dius.pact.core.model.ContentTypeHint;
import au.com.dius.pact.core.model.Interaction;
import au.com.dius.pact.core.model.Pact;
import au.com.dius.pact.provider.MessageAndMetadata;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junit5.PluginTestTarget;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import com.collibra.example.Item;
import com.collibra.example.MailAddress;
import com.collibra.example.Order;
import com.collibra.example.Status;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Provider("order-provider")
@PactBroker(url = "http://localhost:9292")
class PactPulsarProducerTest {
    private static final String AVRO_CONTENT_TYPE = "avro/binary; record=Order";
    private static final String KEY_CONTENT_TYPE = "contentType";
    private static final String KEY_CONTENT_TYPE_HINT = "contentTypeHint";
    private static final ContentTypeHint CONTENT_TYPE_HINT = ContentTypeHint.BINARY;

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void testTemplate(Pact pact, Interaction interaction, PactVerificationContext context) {
        context.verifyInteraction();
    }
    @BeforeEach
    void setupTest(PactVerificationContext context) {
        context.setTarget(new PluginTestTarget(
                Map.of(
                        "host", "localhost",
                        "port", "9292",
                        "transport", "grpc"
                )
        ));
    }

    @PactVerifyProvider("Order Created")
    public MessageAndMetadata orderCreatedEvent() throws IOException {
        Order order = new Order(
                100L,
                "name-1",
                true,
                15.8F,
                1.8D, Status.CREATED, new MailAddress(121, "street name", null),
                List.of(
                        new Item("Item-1", 1L),
                        new Item("Item-2", 2L)
                )
        );

        return new MessageAndMetadata(
                serialise(order),
                Map.of(
                        KEY_CONTENT_TYPE, AVRO_CONTENT_TYPE,
                        KEY_CONTENT_TYPE_HINT, CONTENT_TYPE_HINT
                )
        );
    }

    private byte[] serialise(Order record) throws IOException {
        SpecificDatumWriter<Order> writer = new SpecificDatumWriter<>(Order.class);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
            writer.write(record, encoder);
            encoder.flush();
            return outputStream.toByteArray();
        }
    }
}
