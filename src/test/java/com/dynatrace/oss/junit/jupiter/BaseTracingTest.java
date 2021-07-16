package com.dynatrace.oss.junit.jupiter;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporters.inmemory.InMemorySpanExporter;
import io.opentelemetry.exporters.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class BaseTracingTest {

	private static OpenTelemetry telemetry;
	private static InMemorySpanExporter exporter = InMemorySpanExporter.create();

	protected static OpenTelemetry getOpenTelemetry() {
		if(telemetry == null) {

			String exportHost = System.getProperty("exporter-host", "ep-otel-collector-endpoint.apps.lab.dynatrace.org");
			String exportPort = System.getProperty("exporter-port", "4317");
			ManagedChannel channel =
					ManagedChannelBuilder.forAddress(exportHost, Integer.parseInt(exportPort)).usePlaintext().build();
			OtlpGrpcSpanExporter otlpGrpcSpanExporter =
					OtlpGrpcSpanExporter
							.builder()
							.setChannel(channel)
							.setTimeout(30, TimeUnit.SECONDS)

							.build();

			Runtime.getRuntime().addShutdownHook(new Thread(() -> otlpGrpcSpanExporter.flush()));

			Resource serviceNameResource =
					Resource.create(Attributes.of(stringKey("service.name"), "junit-extension"));

			final SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
					.addSpanProcessor(SimpleSpanProcessor.create(otlpGrpcSpanExporter))
					.addSpanProcessor(SimpleSpanProcessor.create(exporter))
					.setResource(Resource.getDefault().merge(serviceNameResource))

					.build();

			telemetry = OpenTelemetrySdk.builder()
					.setTracerProvider(sdkTracerProvider)
					.setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
					.build();
		}


		return  telemetry;
	}


	@BeforeEach
	public void resetExporter() {
		exporter.reset();
	}

	protected static List<SpanData> getSpanData() {
		exporter.flush();
		return exporter.getFinishedSpanItems();
	}

}
