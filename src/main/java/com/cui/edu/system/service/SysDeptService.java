package com.cui.edu.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.system.entity.SysDept;
import com.cui.edu.vo.system.DeptVO;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
public interface SysDeptService extends IService<SysDept> {

    /**
     * 部门分页查询
     *
     * @param vo 查询参数
     * @return 分页结果
     */
    PageResult findPage(DeptVO vo);

    /**
     * 逻辑删除部门
     *
     * @param ids 部门id集合
     */
    void logicDelete(List<Long> ids);
}
