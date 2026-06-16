package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cui.edu.common.PageResult;
import com.cui.edu.trip.entity.ActivityManage;
import com.cui.edu.trip.mapper.ActivityManageMapper;
import com.cui.edu.vo.trip.ActivityManageVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityManageServiceImplTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ActivityManage.class);
    }

    @Test
    void findPageFiltersByParticipationTypeWhenProvided() {
        ActivityManageMapper activityManageMapper = mock(ActivityManageMapper.class);
        when(activityManageMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        ActivityManageServiceImpl activityManageService = activityManageService(activityManageMapper);
        ActivityManageVO vo = new ActivityManageVO();
        vo.setPageNum(1);
        vo.setPageSize(10);
        vo.setParticipationType(ActivityManage.PARTICIPATION_TYPE_TEAM);

        PageResult pageResult = activityManageService.findPage(vo);

        ArgumentCaptor<Wrapper<ActivityManage>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(activityManageMapper).selectPage(any(Page.class), queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("participation_type"));
        assertTrue(pageResult.getContent().isEmpty());
    }

    @Test
    void findByMuseumIdFiltersByParticipationTypeWhenProvided() {
        ActivityManageMapper activityManageMapper = mock(ActivityManageMapper.class);
        when(activityManageMapper.selectList(any())).thenReturn(Collections.emptyList());
        ActivityManageServiceImpl activityManageService = activityManageService(activityManageMapper);

        activityManageService.findByMuseumId(1L, ActivityManage.PARTICIPATION_TYPE_TEAM);

        ArgumentCaptor<Wrapper<ActivityManage>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(activityManageMapper).selectList(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("participation_type"));
    }

    @Test
    void findByMuseumIdDoesNotFilterByParticipationTypeWhenMissing() {
        ActivityManageMapper activityManageMapper = mock(ActivityManageMapper.class);
        when(activityManageMapper.selectList(any())).thenReturn(Collections.emptyList());
        ActivityManageServiceImpl activityManageService = activityManageService(activityManageMapper);

        activityManageService.findByMuseumId(1L, null);

        ArgumentCaptor<Wrapper<ActivityManage>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(activityManageMapper).selectList(queryCaptor.capture());
        assertFalse(queryCaptor.getValue().getSqlSegment().contains("participation_type"));
    }

    private ActivityManageServiceImpl activityManageService(ActivityManageMapper activityManageMapper) {
        ActivityManageServiceImpl activityManageService = new ActivityManageServiceImpl();
        ReflectionTestUtils.setField(activityManageService, "baseMapper", activityManageMapper);
        return activityManageService;
    }
}
