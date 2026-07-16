package com.cui.edu.trip.service;

import com.cui.edu.vo.reconciliation.ReconciliationAbnormalQueryVO;
import com.cui.edu.vo.reconciliation.ReconciliationAbnormalResult;

public interface ReconciliationService {

    ReconciliationAbnormalResult findAbnormalData(ReconciliationAbnormalQueryVO vo);
}
