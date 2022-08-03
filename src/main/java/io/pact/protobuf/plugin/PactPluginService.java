package io.pact.protobuf.plugin;

import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractBlockingStub;
import io.pact.plugin.PactPluginGrpc;
import io.pact.plugin.Plugin;
import io.pact.plugins.jvm.core.PactPlugin;
import io.pact.plugins.jvm.core.PactPluginManifest;

public class PactPluginService implements PactPlugin {

    Logger logger = LoggerFactory.getLogger(PactPluginService.class);

    @Override
    public List<Plugin.CatalogueEntry> getCatalogueEntries() {
        return List.of(
                Plugin.CatalogueEntry.newBuilder()
                        .setType(Plugin.CatalogueEntry.EntryType.CONTENT_MATCHER)
                        .setKey("avro")
                        .putValues("content-types", "application/protobuf")
                        .build(),
                Plugin.CatalogueEntry.newBuilder()
                        .setType(Plugin.CatalogueEntry.EntryType.CONTENT_GENERATOR)
                        .setKey("protobuf")
                        .putValues("content-types", "application/protobuf")
                        .build());
    }

    @Override
    public void setCatalogueEntries(List<Plugin.CatalogueEntry> list) {}

    @Override
    public ManagedChannel getChannel() {
        return null;
    }

    @Override
    public void setChannel(ManagedChannel managedChannel) {}

    @Override
    public PactPluginManifest getManifest() {
        return null;
    }

    @Override
    public Integer getPort() {
        return null;
    }

    @Override
    public Long getProcessPid() {
        return null;
    }

    @Override
    public String getServerKey() {
        return null;
    }

    @Override
    public AbstractBlockingStub<PactPluginGrpc.PactPluginBlockingStub> getStub() {
        return null;
    }

    @Override
    public void setStub(
            AbstractBlockingStub<PactPluginGrpc.PactPluginBlockingStub> abstractBlockingStub) {}

    @Override
    public void shutdown() {}

    @Override
    public <T> T withGrpcStub(Function<PactPluginGrpc.PactPluginBlockingStub, T> function) {
        return null;
    }
}
