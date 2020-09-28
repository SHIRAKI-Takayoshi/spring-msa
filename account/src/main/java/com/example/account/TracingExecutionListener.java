package com.example.account;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import io.r2dbc.proxy.core.*;
import io.r2dbc.proxy.listener.LifeCycleListener;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

public class TracingExecutionListener implements LifeCycleListener {
    private static final String TAG_CONNECTION_ID = "connectionId";
    private static final String TAG_CONNECTION_CREATE_THREAD_ID = "threadIdOnCreate";
    private static final String TAG_CONNECTION_CLOSE_THREAD_ID = "threadIdOnClose";
    private static final String TAG_CONNECTION_CREATE_THREAD_NAME = "threadNameOnCreate";
    private static final String TAG_CONNECTION_CLOSE_THREAD_NAME = "threadNameOnClose";
    private static final String TAG_THREAD_ID = "threadId";
    private static final String TAG_THREAD_NAME = "threadName";
    private static final String TAG_QUERIES = "queries";
    private static final String TAG_BATCH_SIZE = "batchSize";
    private static final String TAG_QUERY_TYPE = "type";
    private static final String TAG_QUERY_SUCCESS = "success";
    private static final String TAG_QUERY_MAPPED_RESULT_COUNT = "mappedResultCount";
    private static final String TAG_TRANSACTION_SAVEPOINT = "savepoint";
    private static final String TAG_TRANSACTION_COUNT = "transactionCount";
    private static final String TAG_COMMIT_COUNT = "commitCount";
    private static final String TAG_ROLLBACK_COUNT = "rollbackCount";

    static final String CONNECTION_SPAN_KEY = "connectionSpan";
    static final String TRANSACTION_SPAN_KEY = "transactionSpan";
    static final String QUERY_SPAN_KEY = "querySpan";

    private final Tracer tracer;

    public TracingExecutionListener() {
        this.tracer = GlobalTracer.get();
    }

    @Override
    public void beforeCreateOnConnectionFactory(MethodExecutionInfo methodExecutionInfo) {
        Span connectionSpan = this.tracer.buildSpan("r2dbc:connection")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .start();

        // store the span for retrieval at "afterCreateOnConnectionFactory"
        methodExecutionInfo.getValueStore().put("initialConnectionSpan", connectionSpan);
    }

    @Override
    public void afterCreateOnConnectionFactory(MethodExecutionInfo methodExecutionInfo) {
        // retrieve the span created at "beforeCreateOnConnectionFactory"
        Span connectionSpan = methodExecutionInfo.getValueStore().get("initialConnectionSpan", Span.class);

        Throwable thrown = methodExecutionInfo.getThrown();
        if (thrown != null) {
            connectionSpan.setBaggageItem("exception", thrown.toString())
                    .finish();
            return;
        }

        ConnectionInfo connectionInfo = methodExecutionInfo.getConnectionInfo();
        String connectionId = connectionInfo.getConnectionId();

        connectionSpan
                .setTag(TAG_CONNECTION_ID, connectionId)
                .setTag(TAG_CONNECTION_CREATE_THREAD_ID, String.valueOf(methodExecutionInfo.getThreadId()))
                .setTag(TAG_CONNECTION_CREATE_THREAD_NAME, methodExecutionInfo.getThreadName())
                .log("Connection created");

        // store the span in connection scoped value store
        connectionInfo.getValueStore().put(CONNECTION_SPAN_KEY, connectionSpan);
    }

    @Override
    public void afterCloseOnConnection(MethodExecutionInfo methodExecutionInfo) {
        ConnectionInfo connectionInfo = methodExecutionInfo.getConnectionInfo();
        String connectionId = connectionInfo.getConnectionId();
        Span connectionSpan = connectionInfo.getValueStore().get(CONNECTION_SPAN_KEY, Span.class);
        if (connectionSpan == null) {
            return;    // already closed
        }
        Throwable thrown = methodExecutionInfo.getThrown();

        if (thrown != null) {
            connectionSpan.setBaggageItem("exception", thrown.toString());
        }
        connectionSpan
                .setTag(TAG_CONNECTION_ID, connectionId)
                .setTag(TAG_CONNECTION_CLOSE_THREAD_ID, String.valueOf(methodExecutionInfo.getThreadId()))
                .setTag(TAG_CONNECTION_CLOSE_THREAD_NAME, methodExecutionInfo.getThreadName())
                .setTag(TAG_TRANSACTION_COUNT, String.valueOf(connectionInfo.getTransactionCount()))
                .setTag(TAG_COMMIT_COUNT, String.valueOf(connectionInfo.getCommitCount()))
                .setTag(TAG_ROLLBACK_COUNT, String.valueOf(connectionInfo.getRollbackCount()))
                .finish();
    }

    @Override
    public void beforeQuery(QueryExecutionInfo queryExecutionInfo) {
        String connectionId = queryExecutionInfo.getConnectionInfo().getConnectionId();
        String queries = queryExecutionInfo.getQueries().stream()
                .map(QueryInfo::getQuery)
                .collect(Collectors.joining(", "));

        Span querySpan = this.tracer.buildSpan("r2dbc:query")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TAG_CONNECTION_ID, connectionId)
                .withTag(TAG_QUERY_TYPE, queryExecutionInfo.getType().toString())
                .withTag(TAG_QUERIES, queries)
                .start();

        if (ExecutionType.BATCH == queryExecutionInfo.getType()) {
            querySpan.setTag(TAG_BATCH_SIZE, Integer.toString(queryExecutionInfo.getBatchSize()));
        }

        // pass the query span to "afterQuery" method
        queryExecutionInfo.getValueStore().put(QUERY_SPAN_KEY, querySpan);
    }

    @Override
    public void afterQuery(QueryExecutionInfo queryExecutionInfo) {

        Span querySpan = queryExecutionInfo.getValueStore().get(QUERY_SPAN_KEY, Span.class);
        querySpan.setTag(TAG_THREAD_ID, String.valueOf(queryExecutionInfo.getThreadId()))
                .setTag(TAG_THREAD_NAME, queryExecutionInfo.getThreadName())
                .setTag(TAG_QUERY_SUCCESS, Boolean.toString(queryExecutionInfo.isSuccess()));

        Throwable thrown = queryExecutionInfo.getThrowable();
        if (thrown != null) {
            querySpan.setBaggageItem("exception", thrown.toString());
        } else {
            querySpan.setTag(TAG_QUERY_MAPPED_RESULT_COUNT, Integer.toString(queryExecutionInfo.getCurrentResultCount()));
        }
        querySpan.finish();
    }

    @Override
    public void beforeBeginTransactionOnConnection(MethodExecutionInfo methodExecutionInfo) {
        Span transactionSpan = this.tracer.buildSpan("r2dbc:transaction")
                .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
                .start();

        methodExecutionInfo.getConnectionInfo().getValueStore().put(TRANSACTION_SPAN_KEY, transactionSpan);
    }

    @Override
    public void afterCommitTransactionOnConnection(MethodExecutionInfo methodExecutionInfo) {
        ConnectionInfo connectionInfo = methodExecutionInfo.getConnectionInfo();
        String connectionId = connectionInfo.getConnectionId();

        Span transactionSpan = connectionInfo.getValueStore().get(TRANSACTION_SPAN_KEY, Span.class);
        if (transactionSpan != null) {
            transactionSpan.log("Commit")
                    .setTag(TAG_CONNECTION_ID, connectionId)
                    .setTag(TAG_THREAD_ID, String.valueOf(methodExecutionInfo.getThreadId()))
                    .setTag(TAG_THREAD_NAME, methodExecutionInfo.getThreadName())
                    .finish();
        }

        Span connectionSpan = connectionInfo.getValueStore().get(CONNECTION_SPAN_KEY, Span.class);
        if (connectionSpan == null) {
            return;
        }
        connectionSpan.log("Transaction commit");
    }

    @Override
    public void afterRollbackTransactionOnConnection(MethodExecutionInfo methodExecutionInfo) {
        ConnectionInfo connectionInfo = methodExecutionInfo.getConnectionInfo();
        String connectionId = connectionInfo.getConnectionId();

        Span transactionSpan = connectionInfo.getValueStore().get(TRANSACTION_SPAN_KEY, Span.class);
        if (transactionSpan != null) {
            transactionSpan.log("Rollback")
                    .setTag(TAG_CONNECTION_ID, connectionId)
                    .setTag(TAG_THREAD_ID, String.valueOf(methodExecutionInfo.getThreadId()))
                    .setTag(TAG_THREAD_NAME, methodExecutionInfo.getThreadName())
                    .finish();
        }

        Span connectionSpan = connectionInfo.getValueStore().get(CONNECTION_SPAN_KEY, Span.class);
        connectionSpan.log("Transaction rollback");
    }

    @Override
    public void afterRollbackTransactionToSavepointOnConnection(MethodExecutionInfo methodExecutionInfo) {
        ConnectionInfo connectionInfo = methodExecutionInfo.getConnectionInfo();
        String connectionId = connectionInfo.getConnectionId();
        String savepoint = (String) methodExecutionInfo.getMethodArgs()[0];

        Span transactionSpan = connectionInfo.getValueStore().get(TRANSACTION_SPAN_KEY, Span.class);
        if (transactionSpan != null) {
            transactionSpan
                    .log("Rollback to savepoint")
                    .setTag(TAG_TRANSACTION_SAVEPOINT, savepoint)
                    .setTag(TAG_CONNECTION_ID, connectionId)
                    .setTag(TAG_THREAD_ID, String.valueOf(methodExecutionInfo.getThreadId()))
                    .setTag(TAG_THREAD_NAME, methodExecutionInfo.getThreadName())
                    .finish();
        }

        Span connectionSpan = connectionInfo.getValueStore().get(CONNECTION_SPAN_KEY, Span.class);
        connectionSpan.log("Transaction rollback to savepoint");
    }

}
