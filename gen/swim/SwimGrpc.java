package swim;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.59.1)",
    comments = "Source: swim.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class SwimGrpc {

  private SwimGrpc() {}

  public static final java.lang.String SERVICE_NAME = "swim.Swim";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<swim.SwimSpec.SwimMessage,
      swim.SwimSpec.SwimMessage> getSendMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Send",
      requestType = swim.SwimSpec.SwimMessage.class,
      responseType = swim.SwimSpec.SwimMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<swim.SwimSpec.SwimMessage,
      swim.SwimSpec.SwimMessage> getSendMethod() {
    io.grpc.MethodDescriptor<swim.SwimSpec.SwimMessage, swim.SwimSpec.SwimMessage> getSendMethod;
    if ((getSendMethod = SwimGrpc.getSendMethod) == null) {
      synchronized (SwimGrpc.class) {
        if ((getSendMethod = SwimGrpc.getSendMethod) == null) {
          SwimGrpc.getSendMethod = getSendMethod =
              io.grpc.MethodDescriptor.<swim.SwimSpec.SwimMessage, swim.SwimSpec.SwimMessage>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Send"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  swim.SwimSpec.SwimMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  swim.SwimSpec.SwimMessage.getDefaultInstance()))
              .setSchemaDescriptor(new SwimMethodDescriptorSupplier("Send"))
              .build();
        }
      }
    }
    return getSendMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static SwimStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SwimStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SwimStub>() {
        @java.lang.Override
        public SwimStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SwimStub(channel, callOptions);
        }
      };
    return SwimStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static SwimBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SwimBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SwimBlockingStub>() {
        @java.lang.Override
        public SwimBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SwimBlockingStub(channel, callOptions);
        }
      };
    return SwimBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static SwimFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SwimFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SwimFutureStub>() {
        @java.lang.Override
        public SwimFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SwimFutureStub(channel, callOptions);
        }
      };
    return SwimFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void send(swim.SwimSpec.SwimMessage request,
        io.grpc.stub.StreamObserver<swim.SwimSpec.SwimMessage> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSendMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service Swim.
   */
  public static abstract class SwimImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return SwimGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service Swim.
   */
  public static final class SwimStub
      extends io.grpc.stub.AbstractAsyncStub<SwimStub> {
    private SwimStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SwimStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SwimStub(channel, callOptions);
    }

    /**
     */
    public void send(swim.SwimSpec.SwimMessage request,
        io.grpc.stub.StreamObserver<swim.SwimSpec.SwimMessage> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSendMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service Swim.
   */
  public static final class SwimBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<SwimBlockingStub> {
    private SwimBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SwimBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SwimBlockingStub(channel, callOptions);
    }

    /**
     */
    public swim.SwimSpec.SwimMessage send(swim.SwimSpec.SwimMessage request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSendMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service Swim.
   */
  public static final class SwimFutureStub
      extends io.grpc.stub.AbstractFutureStub<SwimFutureStub> {
    private SwimFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SwimFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SwimFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<swim.SwimSpec.SwimMessage> send(
        swim.SwimSpec.SwimMessage request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSendMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SEND = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SEND:
          serviceImpl.send((swim.SwimSpec.SwimMessage) request,
              (io.grpc.stub.StreamObserver<swim.SwimSpec.SwimMessage>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getSendMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              swim.SwimSpec.SwimMessage,
              swim.SwimSpec.SwimMessage>(
                service, METHODID_SEND)))
        .build();
  }

  private static abstract class SwimBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    SwimBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return swim.SwimSpec.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("Swim");
    }
  }

  private static final class SwimFileDescriptorSupplier
      extends SwimBaseDescriptorSupplier {
    SwimFileDescriptorSupplier() {}
  }

  private static final class SwimMethodDescriptorSupplier
      extends SwimBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    SwimMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (SwimGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new SwimFileDescriptorSupplier())
              .addMethod(getSendMethod())
              .build();
        }
      }
    }
    return result;
  }
}
