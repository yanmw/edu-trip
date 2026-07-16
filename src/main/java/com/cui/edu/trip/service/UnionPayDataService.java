package com.cui.edu.trip.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.trip.entity.unionpay.UnionPayData;
import com.cui.edu.vo.reconciliation.ReconciliationVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 银联excel表格数据 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-07-15
 */
public interface UnionPayDataService extends IService<UnionPayData> {

    @Deprecated
    List<Map> billing(ReconciliationVO vo);

    @Deprecated
    Map<String, Collection> abnormalData(ReconciliationVO vo);

    @Deprecated
    Map detail(String tradeNo, String museumId);
}
