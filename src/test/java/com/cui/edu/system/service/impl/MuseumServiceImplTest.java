package com.cui.edu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.mapper.MuseumMapper;
import com.cui.edu.system.service.MuseumSaveResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MuseumServiceImplTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Museum.class);
    }

    @Test
    void saveMuseumReturnsDuplicateNameWhenNewMuseumNameExists() {
        MuseumMapper museumMapper = mock(MuseumMapper.class);
        when(museumMapper.selectCount(any())).thenReturn(1);
        MuseumServiceImpl museumService = museumService(museumMapper);

        Museum museum = new Museum();
        museum.setName("故宫博物院");
        museum.setMid("89800001");

        MuseumSaveResult result = museumService.saveMuseum(museum);

        assertEquals(MuseumSaveResult.DUPLICATE_NAME, result);
    }

    @Test
    void saveMuseumReturnsDuplicateNameWhenUpdatedMuseumNameBelongsToAnotherMuseum() {
        MuseumMapper museumMapper = mock(MuseumMapper.class);
        when(museumMapper.selectCount(any())).thenReturn(1);
        MuseumServiceImpl museumService = museumService(museumMapper);

        Museum museum = new Museum();
        museum.setId(2L);
        museum.setName("故宫博物院");

        MuseumSaveResult result = museumService.saveMuseum(museum);

        assertEquals(MuseumSaveResult.DUPLICATE_NAME, result);
        ArgumentCaptor<Wrapper<Museum>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(museumMapper).selectCount(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("id <>"));
    }

    @Test
    void saveMuseumAllowsUpdatingExistingMuseumWithSameName() {
        MuseumMapper museumMapper = mock(MuseumMapper.class);
        Museum existingMuseum = new Museum();
        existingMuseum.setId(2L);
        existingMuseum.setName("故宫博物院");
        when(museumMapper.selectCount(any())).thenReturn(0);
        when(museumMapper.selectById(eq(2L))).thenReturn(existingMuseum);
        when(museumMapper.updateById(any())).thenReturn(1);
        MuseumServiceImpl museumService = museumService(museumMapper);

        Museum museum = new Museum();
        museum.setId(2L);
        museum.setName("故宫博物院");

        MuseumSaveResult result = museumService.saveMuseum(museum);

        assertEquals(MuseumSaveResult.SUCCESS, result);
    }

    private MuseumServiceImpl museumService(MuseumMapper museumMapper) {
        MuseumServiceImpl museumService = new MuseumServiceImpl();
        ReflectionTestUtils.setField(museumService, "baseMapper", museumMapper);
        return museumService;
    }
}
