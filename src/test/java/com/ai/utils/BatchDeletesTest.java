package com.ai.utils;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchDeletesTest {

    @Test
    @SuppressWarnings("unchecked")
    void purge_loopsUntilPartialBatch_thenStops() {
        BaseMapper<Object> mapper = mock(BaseMapper.class);
        when(mapper.deleteByQuery(any())).thenReturn(BatchDeletes.BATCH_SIZE, BatchDeletes.BATCH_SIZE, 120);

        int total = BatchDeletes.purge(mapper, QueryWrapper::create);

        assertEquals(BatchDeletes.BATCH_SIZE * 2 + 120, total);

        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper, times(3)).deleteByQuery(captor.capture());
        // 每批独立 wrapper，避免有状态 wrapper 跨批复用
        assertNotSame(captor.getAllValues().get(0), captor.getAllValues().get(1));
    }
}
