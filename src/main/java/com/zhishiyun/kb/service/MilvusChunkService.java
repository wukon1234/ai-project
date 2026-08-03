package com.zhishiyun.kb.service;

import com.zhishiyun.kb.common.BizException;
import com.zhishiyun.kb.common.ErrorCode;
import com.zhishiyun.kb.entity.KbChunkEntity;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Milvus/Zilliz 分块向量：ensure collection → insert / search → 按 doc 删除。
 */
@Slf4j
@Service
public class MilvusChunkService {

    private static final int TEXT_MAX = 2000;

    @Value("${kb.milvus.uri:}")
    private String uri;
    @Value("${kb.milvus.token:}")
    private String token;
    @Value("${kb.milvus.collection:kb_chunks}")
    private String collection;
    @Value("${kb.milvus.dimension:1024}")
    private int dimension;

    private final Object lock = new Object();
    private volatile MilvusServiceClient client;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /** 写入向量；返回与 chunks 对齐的 milvus PK（使用 chunk.id）。 */
    public List<String> upsertChunks(List<KbChunkEntity> chunks, List<float[]> vectors) {
        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<String>();
        }
        if (vectors == null || vectors.size() != chunks.size()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "向量条数与分块不一致");
        }
        ensureReady();
        List<Long> ids = new ArrayList<Long>(chunks.size());
        List<List<Float>> embeddings = new ArrayList<List<Float>>(chunks.size());
        List<String> docIds = new ArrayList<String>(chunks.size());
        List<String> kbIds = new ArrayList<String>(chunks.size());
        List<Long> pageNos = new ArrayList<Long>(chunks.size());
        List<Long> chunkIndexes = new ArrayList<Long>(chunks.size());
        List<String> texts = new ArrayList<String>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            KbChunkEntity c = chunks.get(i);
            float[] v = vectors.get(i);
            if (v == null || v.length != dimension) {
                throw new BizException(ErrorCode.SYSTEM_ERROR,
                        "向量维度不匹配: actual=" + (v == null ? -1 : v.length) + ", expect=" + dimension);
            }
            ids.add(c.getId());
            embeddings.add(toFloatList(normalizeL2(v)));
            docIds.add(String.valueOf(c.getDocId()));
            kbIds.add(c.getLibraryCode() == null ? "" : c.getLibraryCode());
            pageNos.add(c.getPageNo() == null ? 0L : c.getPageNo().longValue());
            chunkIndexes.add(c.getChunkIndex() == null ? 0L : c.getChunkIndex().longValue());
            texts.add(truncate(c.getContent(), TEXT_MAX));
        }
        List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field("id", ids),
                new InsertParam.Field("embedding", embeddings),
                new InsertParam.Field("doc_id", docIds),
                new InsertParam.Field("kb_id", kbIds),
                new InsertParam.Field("page_no", pageNos),
                new InsertParam.Field("chunk_index", chunkIndexes),
                new InsertParam.Field("text", texts)
        );
        R<io.milvus.grpc.MutationResult> resp = client().insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(fields)
                .build());
        assertSuccess("insert", resp);
        List<String> pks = new ArrayList<String>(ids.size());
        for (Long id : ids) {
            pks.add(String.valueOf(id));
        }
        return pks;
    }

    /** 按文档删除向量（reindex 前调用）。 */
    public void deleteByDocId(Long docId) {
        if (docId == null) {
            return;
        }
        ensureReady();
        String expr = "doc_id == \"" + docId + "\"";
        R<io.milvus.grpc.MutationResult> resp = client().delete(DeleteParam.newBuilder()
                .withCollectionName(collection)
                .withExpr(expr)
                .build());
        assertSuccess("delete", resp);
    }

    /**
     * ANN 检索（IP + L2 归一化 ≈ 余弦）。返回按分数降序的 chunk id 列表。
     *
     * @param queryVector 查询向量（方法内会 L2 归一化）
     * @param expr        可选标量过滤，如 {@code kb_id in ["hr"]} / {@code doc_id == "13"}
     * @param topK        返回条数
     */
    public List<VectorHit> search(float[] queryVector, String expr, int topK) {
        if (queryVector == null || queryVector.length == 0 || topK <= 0) {
            return Collections.emptyList();
        }
        ensureReady();
        List<Float> q = toFloatList(normalizeL2(queryVector));
        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withMetricType(MetricType.IP)
                .withVectorFieldName("embedding")
                .withTopK(topK)
                .withVectors(Collections.singletonList(q))
                .withOutFields(Arrays.asList("id", "doc_id", "kb_id", "page_no"))
                .withParams("{\"ef\":64}");
        if (StringUtils.hasText(expr)) {
            builder.withExpr(expr);
        }
        R<io.milvus.grpc.SearchResults> resp = client().search(builder.build());
        assertSuccess("search", resp);
        if (resp.getData() == null || resp.getData().getResults() == null) {
            return Collections.emptyList();
        }
        SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);
        List<VectorHit> hits = new ArrayList<VectorHit>(idScores.size());
        for (SearchResultsWrapper.IDScore is : idScores) {
            hits.add(new VectorHit(is.getLongID(), is.getScore()));
        }
        return hits;
    }

    @Data
    @AllArgsConstructor
    public static class VectorHit {
        private long id;
        private float score;
    }

    private void ensureReady() {
        if (ready.get()) {
            return;
        }
        synchronized (lock) {
            if (ready.get()) {
                return;
            }
            MilvusServiceClient c = client();
            R<Boolean> has = c.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collection)
                    .build());
            assertSuccess("hasCollection", has);
            boolean exists = Boolean.TRUE.equals(has.getData());
            if (exists && !dimensionMatches(c)) {
                log.warn("Milvus collection {} dimension mismatch, dropping and recreating for dim={}",
                        collection, dimension);
                R<RpcStatus> drop = c.dropCollection(DropCollectionParam.newBuilder()
                        .withCollectionName(collection)
                        .build());
                assertSuccess("dropCollection", drop);
                exists = false;
            }
            if (!exists) {
                createCollection(c);
            }
            R<RpcStatus> load = c.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(collection)
                    .build());
            assertSuccess("loadCollection", load);
            ready.set(true);
        }
    }

    private boolean dimensionMatches(MilvusServiceClient c) {
        R<DescribeCollectionResponse> desc = c.describeCollection(DescribeCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        if (desc.getStatus() != R.Status.Success.getCode() || desc.getData() == null) {
            return false;
        }
        for (io.milvus.grpc.FieldSchema field : desc.getData().getSchema().getFieldsList()) {
            if ("embedding".equals(field.getName())) {
                return field.getTypeParamsList().stream()
                        .anyMatch(p -> "dim".equals(p.getKey())
                                && String.valueOf(dimension).equals(p.getValue()));
            }
        }
        return false;
    }

    private void createCollection(MilvusServiceClient c) {
        FieldType id = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();
        FieldType embedding = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(dimension)
                .build();
        FieldType docId = FieldType.newBuilder()
                .withName("doc_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build();
        FieldType kbId = FieldType.newBuilder()
                .withName("kb_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build();
        FieldType pageNo = FieldType.newBuilder()
                .withName("page_no")
                .withDataType(DataType.Int64)
                .build();
        FieldType chunkIndex = FieldType.newBuilder()
                .withName("chunk_index")
                .withDataType(DataType.Int64)
                .build();
        FieldType text = FieldType.newBuilder()
                .withName("text")
                .withDataType(DataType.VarChar)
                .withMaxLength(TEXT_MAX)
                .build();
        R<RpcStatus> create = c.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withDescription("zhishiyun kb chunks")
                .addFieldType(id)
                .addFieldType(embedding)
                .addFieldType(docId)
                .addFieldType(kbId)
                .addFieldType(pageNo)
                .addFieldType(chunkIndex)
                .addFieldType(text)
                .build());
        assertSuccess("createCollection", create);

        R<RpcStatus> index = c.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName("embedding")
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.IP)
                .withExtraParam("{\"M\":16,\"efConstruction\":200}")
                .build());
        assertSuccess("createIndex", index);
        log.info("Milvus collection {} created dim={}", collection, dimension);
    }

    private MilvusServiceClient client() {
        if (client != null) {
            return client;
        }
        synchronized (lock) {
            if (client != null) {
                return client;
            }
            if (!StringUtils.hasText(uri) || !StringUtils.hasText(token)) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "Milvus uri/token 未配置");
            }
            ConnectParam.Builder builder = ConnectParam.newBuilder()
                    .withUri(uri)
                    .withToken(token);
            client = new MilvusServiceClient(builder.build());
            return client;
        }
    }

    private static List<Float> toFloatList(float[] v) {
        List<Float> list = new ArrayList<Float>(v.length);
        for (float f : v) {
            list.add(f);
        }
        return list;
    }

    /** L2 归一化，使 IP 度量近似余弦相似度（约落在 [-1,1]）。 */
    static float[] normalizeL2(float[] v) {
        if (v == null || v.length == 0) {
            return v;
        }
        double sum = 0;
        for (float f : v) {
            sum += (double) f * f;
        }
        if (sum <= 0) {
            return v;
        }
        float norm = (float) Math.sqrt(sum);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = v[i] / norm;
        }
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void assertSuccess(String op, R<?> resp) {
        if (resp == null || resp.getStatus() != R.Status.Success.getCode()) {
            String msg = resp == null ? "null" : resp.getMessage();
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Milvus " + op + " 失败: " + msg);
        }
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
