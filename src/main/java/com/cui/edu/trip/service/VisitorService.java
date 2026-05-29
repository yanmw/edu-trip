package com.cui.edu.trip.service;

import com.cui.edu.trip.entity.Visitor;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.vo.trip.VisitorVO;

import java.util.List;

/**
 * <p>
 * 游客表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface VisitorService extends IService<Visitor> {

    /**
     * 保存游客；保存前根据身份证补齐省份和性别。
     */
    void saveVisitor(Visitor record);

    /**
     * 分页查询未删除的游客。
     */
    PageResult findPage(VisitorVO vo);

    /**
     * 批量逻辑删除游客。
     */
    void logicDelete(List<Long> ids);
}
