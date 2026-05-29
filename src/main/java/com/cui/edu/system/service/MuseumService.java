package com.cui.edu.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.vo.system.MuseumVO;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
public interface MuseumService extends IService<Museum> {

    /**
     * 博物馆分页查询
     *
     * @param vo 查询参数
     * @return 分页结果
     */
    PageResult findPage(MuseumVO vo);

    /**
     * 逻辑删除博物馆
     *
     * @param ids 博物馆id集合
     */
    void logicDelete(List<Long> ids);
}
