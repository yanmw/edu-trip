package com.cui.edu.trip.service;

import com.cui.edu.trip.entity.Visitor;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.vo.trip.VisitorVO;
import org.springframework.web.multipart.MultipartFile;

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
     * 保存游客；保存前根据身份证或手机号补齐省市和性别。
     */
    void saveVisitor(Visitor record);

    /**
     * 导入游客Excel，并为导入数据统一设置团队ID。
     */
    int importExcel(MultipartFile file, Long teamId, String batchNo);

    /**
     * 生成游客导入模板。
     */
    byte[] getImportTemplate();

    /**
     * 分页查询未删除的游客。
     */
    PageResult findPage(VisitorVO vo);

    /**
     * 批量逻辑删除游客。
     */
    void logicDelete(List<Long> ids);

    /**
     * 根据微信openid查询未删除的游客详情。
     */
    Visitor findByWechatOpenid(String wechatOpenid);

    /**
     * 根据团队ID和批次号查询未删除的游客列表。
     */
    List<Visitor> findByTeamId(Long teamId, String batchNo);
}
