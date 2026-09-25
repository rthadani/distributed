package raft;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.59.1)",
    comments = "Source: raft.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class RaftGrpc {

  private RaftGrpc() {}

  public static final java.lang.String SERVICE_NAME = "raft.Raft";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<raft.RaftSpec.AppendEntries,
      raft.RaftSpec.AppendEntriesResponse> getAppendEntriesRPCMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "AppendEntriesRPC",
      requestType = raft.RaftSpec.AppendEntries.class,
      responseType = raft.RaftSpec.AppendEntriesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<raft.RaftSpec.AppendEntries,
      raft.RaftSpec.AppendEntriesResponse> getAppendEntriesRPCMethod() {
    io.grpc.MethodDescriptor<raft.RaftSpec.AppendEntries, raft.RaftSpec.AppendEntriesResponse> getAppendEntriesRPCMethod;
    if ((getAppendEntriesRPCMethod = RaftGrpc.getAppendEntriesRPCMethod) == null) {
      synchronized (RaftGrpc.class) {
        if ((getAppendEntriesRPCMethod = RaftGrpc.getAppendEntriesRPCMethod) == null) {
          RaftGrpc.getAppendEntriesRPCMethod = getAppendEntriesRPCMethod =
              io.grpc.MethodDescriptor.<raft.RaftSpec.AppendEntries, raft.RaftSpec.AppendEntriesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "AppendEntriesRPC"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  raft.RaftSpec.AppendEntries.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  raft.RaftSpec.AppendEntriesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RaftMethodDescriptorSupplier("AppendEntriesRPC"))
              .build();
        }
      }
    }
    return getAppendEntriesRPCMethod;
  }

  private static volatile io.grpc.MethodDescriptor<raft.RaftSpec.RequestVote,
      raft.RaftSpec.RequestVoteResponse> getRequestVoteRPCMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RequestVoteRPC",
      requestType = raft.RaftSpec.RequestVote.class,
      responseType = raft.RaftSpec.RequestVoteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<raft.RaftSpec.RequestVote,
      raft.RaftSpec.RequestVoteResponse> getRequestVoteRPCMethod() {
    io.grpc.MethodDescriptor<raft.RaftSpec.RequestVote, raft.RaftSpec.RequestVoteResponse> getRequestVoteRPCMethod;
    if ((getRequestVoteRPCMethod = RaftGrpc.getRequestVoteRPCMethod) == null) {
      synchronized (RaftGrpc.class) {
        if ((getRequestVoteRPCMethod = RaftGrpc.getRequestVoteRPCMethod) == null) {
          RaftGrpc.getRequestVoteRPCMethod = getRequestVoteRPCMethod =
              io.grpc.MethodDescriptor.<raft.RaftSpec.RequestVote, raft.RaftSpec.RequestVoteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RequestVoteRPC"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  raft.RaftSpec.RequestVote.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  raft.RaftSpec.RequestVoteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RaftMethodDescriptorSupplier("RequestVoteRPC"))
              .build();
        }
      }
    }
    return getRequestVoteRPCMethod;
  }

  private static volatile io.grpc.MethodDescriptor<raft.RaftSpec.SubmitCommand,
      raft.RaftSpec.SubmitCommandResponse> getSubmitCommandRPCMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SubmitCommandRPC",
      requestType = raft.RaftSpec.SubmitCommand.class,
      responseType = raft.RaftSpec.SubmitCommandResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<raft.RaftSpec.SubmitCommand,
      raft.RaftSpec.SubmitCommandResponse> getSubmitCommandRPCMethod() {
    io.grpc.MethodDescriptor<raft.RaftSpec.SubmitCommand, raft.RaftSpec.SubmitCommandResponse> getSubmitCommandRPCMethod;
    if ((getSubmitCommandRPCMethod = RaftGrpc.getSubmitCommandRPCMethod) == null) {
      synchronized (RaftGrpc.class) {
        if ((getSubmitCommandRPCMethod = RaftGrpc.getSubmitCommandRPCMethod) == null) {
          RaftGrpc.getSubmitCommandRPCMethod = getSubmitCommandRPCMethod =
              io.grpc.MethodDescriptor.<raft.RaftSpec.SubmitCommand, raft.RaftSpec.SubmitCommandResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SubmitCommandRPC"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  raft.RaftSpec.SubmitCommand.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  raft.RaftSpec.SubmitCommandResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RaftMethodDescriptorSupplier("SubmitCommandRPC"))
              .build();
        }
      }
    }
    return getSubmitCommandRPCMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static RaftStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RaftStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RaftStub>() {
        @java.lang.Override
        public RaftStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RaftStub(channel, callOptions);
        }
      };
    return RaftStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static RaftBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RaftBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RaftBlockingStub>() {
        @java.lang.Override
        public RaftBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RaftBlockingStub(channel, callOptions);
        }
      };
    return RaftBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static RaftFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RaftFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RaftFutureStub>() {
        @java.lang.Override
        public RaftFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RaftFutureStub(channel, callOptions);
        }
      };
    return RaftFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void appendEntriesRPC(raft.RaftSpec.AppendEntries request,
        io.grpc.stub.StreamObserver<raft.RaftSpec.AppendEntriesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getAppendEntriesRPCMethod(), responseObserver);
    }

    /**
     */
    default void requestVoteRPC(raft.RaftSpec.RequestVote request,
        io.grpc.stub.StreamObserver<raft.RaftSpec.RequestVoteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRequestVoteRPCMethod(), responseObserver);
    }

    /**
     */
    default void submitCommandRPC(raft.RaftSpec.SubmitCommand request,
        io.grpc.stub.StreamObserver<raft.RaftSpec.SubmitCommandResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubmitCommandRPCMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service Raft.
   */
  public static abstract class RaftImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return RaftGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service Raft.
   */
  public static final class RaftStub
      extends io.grpc.stub.AbstractAsyncStub<RaftStub> {
    private RaftStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RaftStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RaftStub(channel, callOptions);
    }

    /**
     */
    public void appendEntriesRPC(raft.RaftSpec.AppendEntries request,
        io.grpc.stub.StreamObserver<raft.RaftSpec.AppendEntriesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getAppendEntriesRPCMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void requestVoteRPC(raft.RaftSpec.RequestVote request,
        io.grpc.stub.StreamObserver<raft.RaftSpec.RequestVoteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRequestVoteRPCMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void submitCommandRPC(raft.RaftSpec.SubmitCommand request,
        io.grpc.stub.StreamObserver<raft.RaftSpec.SubmitCommandResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSubmitCommandRPCMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service Raft.
   */
  public static final class RaftBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<RaftBlockingStub> {
    private RaftBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RaftBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RaftBlockingStub(channel, callOptions);
    }

    /**
     */
    public raft.RaftSpec.AppendEntriesResponse appendEntriesRPC(raft.RaftSpec.AppendEntries request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getAppendEntriesRPCMethod(), getCallOptions(), request);
    }

    /**
     */
    public raft.RaftSpec.RequestVoteResponse requestVoteRPC(raft.RaftSpec.RequestVote request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRequestVoteRPCMethod(), getCallOptions(), request);
    }

    /**
     */
    public raft.RaftSpec.SubmitCommandResponse submitCommandRPC(raft.RaftSpec.SubmitCommand request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSubmitCommandRPCMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service Raft.
   */
  public static final class RaftFutureStub
      extends io.grpc.stub.AbstractFutureStub<RaftFutureStub> {
    private RaftFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RaftFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RaftFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<raft.RaftSpec.AppendEntriesResponse> appendEntriesRPC(
        raft.RaftSpec.AppendEntries request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getAppendEntriesRPCMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<raft.RaftSpec.RequestVoteResponse> requestVoteRPC(
        raft.RaftSpec.RequestVote request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRequestVoteRPCMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<raft.RaftSpec.SubmitCommandResponse> submitCommandRPC(
        raft.RaftSpec.SubmitCommand request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSubmitCommandRPCMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_APPEND_ENTRIES_RPC = 0;
  private static final int METHODID_REQUEST_VOTE_RPC = 1;
  private static final int METHODID_SUBMIT_COMMAND_RPC = 2;

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
        case METHODID_APPEND_ENTRIES_RPC:
          serviceImpl.appendEntriesRPC((raft.RaftSpec.AppendEntries) request,
              (io.grpc.stub.StreamObserver<raft.RaftSpec.AppendEntriesResponse>) responseObserver);
          break;
        case METHODID_REQUEST_VOTE_RPC:
          serviceImpl.requestVoteRPC((raft.RaftSpec.RequestVote) request,
              (io.grpc.stub.StreamObserver<raft.RaftSpec.RequestVoteResponse>) responseObserver);
          break;
        case METHODID_SUBMIT_COMMAND_RPC:
          serviceImpl.submitCommandRPC((raft.RaftSpec.SubmitCommand) request,
              (io.grpc.stub.StreamObserver<raft.RaftSpec.SubmitCommandResponse>) responseObserver);
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
          getAppendEntriesRPCMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              raft.RaftSpec.AppendEntries,
              raft.RaftSpec.AppendEntriesResponse>(
                service, METHODID_APPEND_ENTRIES_RPC)))
        .addMethod(
          getRequestVoteRPCMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              raft.RaftSpec.RequestVote,
              raft.RaftSpec.RequestVoteResponse>(
                service, METHODID_REQUEST_VOTE_RPC)))
        .addMethod(
          getSubmitCommandRPCMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              raft.RaftSpec.SubmitCommand,
              raft.RaftSpec.SubmitCommandResponse>(
                service, METHODID_SUBMIT_COMMAND_RPC)))
        .build();
  }

  private static abstract class RaftBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    RaftBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return raft.RaftSpec.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("Raft");
    }
  }

  private static final class RaftFileDescriptorSupplier
      extends RaftBaseDescriptorSupplier {
    RaftFileDescriptorSupplier() {}
  }

  private static final class RaftMethodDescriptorSupplier
      extends RaftBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    RaftMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (RaftGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new RaftFileDescriptorSupplier())
              .addMethod(getAppendEntriesRPCMethod())
              .addMethod(getRequestVoteRPCMethod())
              .addMethod(getSubmitCommandRPCMethod())
              .build();
        }
      }
    }
    return result;
  }
}
