package com.cui.edu.trip.service.impl;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.config.exception.MyException;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.AdministrativeDivision;
import com.cui.edu.trip.entity.MobileNumberSegment;
import com.cui.edu.trip.entity.Visitor;
import com.cui.edu.trip.mapper.VisitorMapper;
import com.cui.edu.trip.service.AdministrativeDivisionService;
import com.cui.edu.trip.service.MobileNumberSegmentService;
import com.cui.edu.trip.service.VisitorService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.vo.trip.VisitorVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <p>
 * 游客表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Service
public class VisitorServiceImpl extends ServiceImpl<VisitorMapper, Visitor> implements VisitorService {

    /** Excel 导入模板表头（固定顺序） */
    private static final List<String> IMPORT_TEMPLATE_HEADERS = Arrays.asList("姓名", "手机号", "身份证号");

    /** Excel 导入必须包含的字段集合（用于表头校验） */
    private static final Set<String> IMPORT_REQUIRED_FIELDS = new HashSet<>(Arrays.asList("name", "mobile", "idCard"));

    /** 手机号正则：1[3-9] 开头，共 11 位数字 */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 15 位身份证正则：全数字 */
    private static final Pattern ID_CARD_15_PATTERN = Pattern.compile("^\\d{15}$");

    /** 18 位身份证正则：前 17 位数字 + 末位数字或 X */
    private static final Pattern ID_CARD_18_PATTERN = Pattern.compile("^\\d{17}[0-9Xx]$");

    /** 严格日期格式化器，用于校验身份证中的出生日期（拒绝不合法日期如 0229） */
    private static final DateTimeFormatter STRICT_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    /** 18 位身份证加权因子（GB 11643-1999） */
    private static final int[] ID_CARD_WEIGHT = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    /** 18 位身份证校验码对照表，下标为加权和对 11 取模的结果 */
    private static final char[] ID_CARD_CHECK_CODE = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    @Autowired
    private MuseumService museumService;

    @Autowired
    private MobileNumberSegmentService mobileNumberSegmentService;

    @Autowired
    private AdministrativeDivisionService administrativeDivisionService;

    /**
     * 保存游客信息（新增或修改）。
     * <p>
     * 执行顺序：
     * <ol>
     *   <li>{@link #validateMobile} — 手机号非空且格式合法</li>
     *   <li>{@link #validateIdCardFormat} — 身份证可为空；不为空时校验格式、出生日期、地址码、校验码</li>
     *   <li>{@link #validateIdCardByMuseumConfig} — 博物馆 ID 非空且存在</li>
     *   <li>{@link #mergeExistingVisitorByOpenid} — wechatOpenid 已绑定记录时复用其主键，转为更新</li>
     *   <li>{@link #fillProvinceCityAndGender} — 自动补全省市和性别</li>
     *   <li>{@link #restoreDeletedVisitor} — 新增时若 openid 曾被软删除则恢复，不再插入新行</li>
     *   <li>saveOrUpdate — 执行最终的新增或更新操作</li>
     * </ol>
     * </p>
     */
    @Override
    public void saveVisitor(Visitor record) {
        validateMobile(record.getMobile());
        validateIdCardFormat(record.getIdCard());
        validateIdCardByMuseumConfig(record);
        // 若 wechat_openid 已存在，则合并到已有记录（更新）而非报错
        mergeExistingVisitorByOpenid(record);
        // 身份证优先补省市和性别；身份证为空时，按手机号号段补省市，性别置为未知
        fillProvinceCityAndGender(record);
        if (record.getId() == null && record.getIsDeleted() == null) {
            record.setIsDeleted(SysConstants.IS_FALSE);
        }
        // 新增时检查 openid 是否曾被软删除，是则恢复已有记录而非插入新行
        if (record.getId() == null && restoreDeletedVisitor(record)) {
            return;
        }
        super.saveOrUpdate(record);
    }

    /**
     * 通过 Excel 批量导入游客。
     * <p>
     * 采用两阶段处理：
     * <ol>
     *   <li>第一阶段：遍历所有数据行，逐行校验手机号和身份证格式，收集所有错误，不中断。</li>
     *   <li>第二阶段：若第一阶段存在错误，汇总后返回前端，不执行入库；全部通过才批量插入。</li>
     * </ol>
     * 这样前端可以一次性看到所有问题行，而不是每次只看到第一个错误。
     * </p>
     *
     * @return 实际导入的行数
     */
    @Override
    public int importExcel(MultipartFile file, Long teamId, String batchNo) {
        if (file == null || file.isEmpty()) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "导入文件不能为空");
        }

        try (ExcelReader reader = ExcelUtil.getReader(file.getInputStream())) {
            // 第一步：校验表头，必须且只能包含姓名、手机号、身份证号
            validateImportHeaders(reader.readRow(0));
            addHeaderAlias(reader);
            List<Visitor> visitorList = reader.readAll(Visitor.class);
            if (visitorList == null || visitorList.isEmpty()) {
                return 0;
            }

            // 第二步：逐行校验，收集所有错误（不立即中断，让前端一次看到所有问题）
            List<String> errors = new ArrayList<>();
            for (int i = 0; i < visitorList.size(); i++) {
                Visitor visitor = visitorList.get(i);
                // Excel 第1行为表头，数据从第2行开始，行号 = 索引 + 2
                int rowNum = i + 2;
                try {
                    validateMobile(visitor.getMobile());
                } catch (MyException e) {
                    errors.add("第" + rowNum + "行：" + e.getMessage());
                }
                try {
                    validateIdCardFormat(visitor.getIdCard());
                } catch (MyException e) {
                    errors.add("第" + rowNum + "行：" + e.getMessage());
                }
            }

            // 第三步：有校验错误时，汇总后统一抛出，不执行入库
            if (!errors.isEmpty()) {
                throw new MyException(HttpStatus.SC_BAD_REQUEST, String.join("\n", errors));
            }

            // 第四步：全部校验通过，补全字段后批量入库
            for (Visitor visitor : visitorList) {
                // 清除 Excel 中可能携带的 id，确保以新增方式入库
                visitor.setId(null);
                visitor.setTeamId(teamId);
                visitor.setBatchNo(batchNo);
                visitor.setIsDeleted(SysConstants.IS_FALSE);
                // 沿用同一套省市和性别补齐规则
                fillProvinceCityAndGender(visitor);
            }
            super.saveBatch(visitorList);
            return visitorList.size();
        } catch (IOException e) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "Excel导入失败");
        }
    }

    /**
     * 生成游客导入 Excel 模板（仅含表头，无数据行）。
     */
    @Override
    public byte[] getImportTemplate() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ExcelWriter writer = ExcelUtil.getWriter(true)) {
            writer.writeHeadRow(IMPORT_TEMPLATE_HEADERS);
            writer.setColumnWidth(0, 12);
            writer.setColumnWidth(1, 18);
            writer.setColumnWidth(2, 24);
            writer.flush(outputStream, true);
        }
        return outputStream.toByteArray();
    }

    /**
     * 分页查询未删除的游客。
     * <p>
     * 所有条件均为可选，不传则不过滤该字段。
     * 姓名、手机号、身份证号使用模糊匹配；其余字段精确匹配。
     * 结果按主键倒序排列（最新入库的在前）。
     * </p>
     */
    @Override
    public PageResult findPage(VisitorVO vo) {
        Page<Visitor> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<Visitor> ew = new QueryWrapper<>();
        if (StringUtils.isNotBlank(vo.getName())) {
            ew.like(Visitor.NAME, vo.getName());
        }
        if (StringUtils.isNotBlank(vo.getMobile())) {
            ew.like(Visitor.MOBILE, vo.getMobile());
        }
        if (StringUtils.isNotBlank(vo.getIdCard())) {
            ew.like(Visitor.ID_CARD, vo.getIdCard());
        }
        if (StringUtils.isNotBlank(vo.getWechatOpenid())) {
            ew.eq(Visitor.WECHAT_OPENID, vo.getWechatOpenid());
        }
        if (vo.getMuseumId() != null) {
            ew.eq(Visitor.MUSEUM_ID, vo.getMuseumId());
        }
        if (StringUtils.isNotBlank(vo.getProvince())) {
            ew.eq(Visitor.PROVINCE, vo.getProvince());
        }
        if (StringUtils.isNotBlank(vo.getCity())) {
            ew.eq(Visitor.CITY, vo.getCity());
        }
        if (vo.getGender() != null) {
            ew.eq(Visitor.GENDER, vo.getGender());
        }
        if (vo.getTeamId() != null) {
            ew.eq(Visitor.TEAM_ID, vo.getTeamId());
        }
        if (StringUtils.isNotBlank(vo.getBatchNo())) {
            ew.eq(Visitor.BATCH_NO, vo.getBatchNo());
        }
        ew.eq(Visitor.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(Visitor.ID);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 批量逻辑删除游客（软删除）。
     * <p>
     * 仅将 is_deleted 字段置为 1，不物理删除数据，
     * 保留历史记录供审计追溯。
     * </p>
     */
    @Override
    public void logicDelete(List<Long> ids) {
        List<Visitor> visitorList = new ArrayList<>();
        for (Long id : ids) {
            Visitor visitor = new Visitor();
            visitor.setId(id);
            visitor.setIsDeleted(SysConstants.IS_TRUE);
            visitorList.add(visitor);
        }
        super.updateBatchById(visitorList);
    }

    /**
     * 根据微信 openid 查询未删除的游客详情。
     * <p>
     * 若存在多条记录（数据异常），取主键最大的一条（最新记录）。
     * 未找到时返回 null。
     * </p>
     */
    @Override
    public Visitor findByWechatOpenid(String wechatOpenid) {
        QueryWrapper<Visitor> ew = new QueryWrapper<>();
        ew.eq(Visitor.WECHAT_OPENID, wechatOpenid);
        ew.eq(Visitor.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(Visitor.ID);
        ew.last("limit 1");
        return super.getOne(ew);
    }

    /**
     * 根据团队 ID 和批次号查询未删除的游客列表。
     * <p>
     * batchNo 为空时查询该团队下全部游客；不为空时按批次过滤。
     * 结果按主键倒序排列。
     * </p>
     */
    @Override
    public List<Visitor> findByTeamId(Long teamId, String batchNo) {
        QueryWrapper<Visitor> ew = new QueryWrapper<>();
        ew.eq(Visitor.TEAM_ID, teamId);
        if (StringUtils.isNotBlank(batchNo)) {
            ew.eq(Visitor.BATCH_NO, batchNo);
        }
        ew.eq(Visitor.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(Visitor.ID);
        return super.list(ew);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 若 wechat_openid 已绑定另一条未删除记录，则将该记录的 id 赋给 record，
     * 使后续 saveOrUpdate 执行 UPDATE 而不是 INSERT，避免重复 openid 报错。
     * <p>
     * 场景：小程序用户重新填写资料时，openid 已存在，直接更新而非新增。
     * </p>
     */
    private void mergeExistingVisitorByOpenid(Visitor record) {
        if (StringUtils.isBlank(record.getWechatOpenid())) {
            return;
        }
        QueryWrapper<Visitor> ew = new QueryWrapper<>();
        ew.eq(Visitor.WECHAT_OPENID, record.getWechatOpenid());
        ew.eq(Visitor.IS_DELETED, SysConstants.IS_FALSE);
        if (record.getId() != null) {
            // 修改时排除自身，避免误判
            ew.ne(Visitor.ID, record.getId());
        }
        ew.orderByDesc(Visitor.ID);
        ew.last("limit 1");
        Visitor existing = super.getOne(ew);
        if (existing != null) {
            // openid 已存在，复用已有记录的主键，后续变为更新操作
            record.setId(existing.getId());
        }
    }

    /**
     * 新增场景下，若 wechat_openid 已存在于软删除记录，则恢复该记录（而非插入新行）。
     * <p>
     * 恢复逻辑：复用原记录主键，将 is_deleted 置回 0，其余字段以传入数据覆盖。
     * </p>
     *
     * @return true 表示已恢复软删除记录，调用方无需再执行 saveOrUpdate
     */
    private boolean restoreDeletedVisitor(Visitor record) {
        if (StringUtils.isBlank(record.getWechatOpenid())) {
            return false;
        }
        Visitor deletedVisitor = findDeletedByWechatOpenid(record.getWechatOpenid());
        if (deletedVisitor == null) {
            return false;
        }
        record.setId(deletedVisitor.getId());
        record.setIsDeleted(SysConstants.IS_FALSE);
        super.updateById(record);
        return true;
    }

    /**
     * 根据 wechat_openid 查询已软删除的游客记录（is_deleted = 1）。
     * 若存在多条，取主键最大的一条。
     */
    private Visitor findDeletedByWechatOpenid(String wechatOpenid) {
        QueryWrapper<Visitor> ew = new QueryWrapper<>();
        ew.eq(Visitor.WECHAT_OPENID, wechatOpenid);
        ew.eq(Visitor.IS_DELETED, SysConstants.IS_TRUE);
        ew.orderByDesc(Visitor.ID);
        ew.last("limit 1");
        return super.getOne(ew);
    }

    /**
     * 校验手机号：不能为空，且必须符合大陆手机号格式（1[3-9]开头，共 11 位）。
     */
    private void validateMobile(String mobile) {
        if (StringUtils.isBlank(mobile)) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "手机号不能为空");
        }
        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "手机号格式不正确");
        }
    }

    /**
     * 校验身份证格式（身份证可为空，为空时直接跳过）。
     * <p>
     * 校验内容：
     * <ul>
     *   <li>15 位：全数字 + 出生日期合法 + 地址码存在</li>
     *   <li>18 位：前 17 位数字末位数字或X + 出生日期合法 + 地址码存在 + 校验码正确</li>
     *   <li>其他长度或格式：直接报错</li>
     * </ul>
     * </p>
     */
    private void validateIdCardFormat(String idCard) {
        if (StringUtils.isBlank(idCard)) {
            return;
        }
        if (ID_CARD_15_PATTERN.matcher(idCard).matches()) {
            // 15 位身份证出生年份补全为 19xx
            validateIdCardBirthDate("19" + idCard.substring(6, 12));
            validateIdCardAddressCode(idCard.substring(0, 6));
            return;
        }
        if (ID_CARD_18_PATTERN.matcher(idCard).matches()) {
            validateIdCardBirthDate(idCard.substring(6, 14));
            validateIdCardAddressCode(idCard.substring(0, 6));
            validateIdCardCheckCode(idCard);
            return;
        }
        throw new MyException(HttpStatus.SC_BAD_REQUEST, "身份证号格式不正确");
    }

    /**
     * 校验身份证中的出生日期是否为合法日期（使用严格模式，拒绝如 20000229 等不合法日期）。
     *
     * @param birthDate 格式为 yyyyMMdd 的出生日期字符串
     */
    private void validateIdCardBirthDate(String birthDate) {
        try {
            LocalDate.parse(birthDate, STRICT_DATE_FORMATTER);
        } catch (Exception e) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "身份证号出生日期不正确");
        }
    }

    /**
     * 校验 18 位身份证的校验码（GB 11643-1999 算法）。
     * <p>
     * 算法：前 17 位按加权因子加权求和，对 11 取模，查对照表得预期校验码，
     * 与第 18 位比较（不区分大小写）。
     * </p>
     */
    private void validateIdCardCheckCode(String idCard) {
        int sum = 0;
        for (int i = 0; i < ID_CARD_WEIGHT.length; i++) {
            sum += (idCard.charAt(i) - '0') * ID_CARD_WEIGHT[i];
        }
        char expectedCheckCode = ID_CARD_CHECK_CODE[sum % 11];
        if (Character.toUpperCase(idCard.charAt(17)) != expectedCheckCode) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "身份证号校验码不正确");
        }
    }

    /**
     * 校验身份证地址码前 6 位对应的省级行政区是否存在于行政区划表中。
     *
     * @param addressCode 身份证前 6 位地址码
     */
    private void validateIdCardAddressCode(String addressCode) {
        String provinceCode = addressCode.substring(0, 2) + "0000";
        if (getAdministrativeDivision(provinceCode) == null) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "身份证号地址码不正确");
        }
    }

    /**
     * 校验博物馆 ID 是否合法（修改时允许从已有记录中取 museumId）。
     * <p>
     * 校验内容：museumId 不能为空，且对应博物馆必须存在。
     * 不再强制要求身份证非空，身份证是否必填由调用方或前端控制。
     * </p>
     */
    private void validateIdCardByMuseumConfig(Visitor record) {
        // 修改时若未传 museumId，从已有记录中补充
        Visitor oldVisitor = record.getId() == null ? null : super.getById(record.getId());
        Long museumId = record.getMuseumId() != null
                ? record.getMuseumId()
                : oldVisitor == null ? null : oldVisitor.getMuseumId();
        if (museumId == null) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "博物馆ID不能为空");
        }
        Museum museum = museumService.getById(museumId);
        if (museum == null) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "博物馆不存在");
        }
    }

    /**
     * 自动补全游客的省市和性别。
     * <p>
     * 优先级：身份证 > 手机号号段。
     * <ul>
     *   <li>有身份证：从身份证地址码解析省市，不足时用手机号号段补充；从顺序码解析性别</li>
     *   <li>无身份证：从手机号号段解析省市；性别置为"未知"</li>
     * </ul>
     * </p>
     */
    private void fillProvinceCityAndGender(Visitor record) {
        String idCard = record.getIdCard();
        if (StringUtils.isBlank(idCard)) {
            fillProvinceAndCityByMobile(record);
            record.setGender(Visitor.GENDER_UNKNOWN);
            return;
        }
        fillProvinceAndCityByIdCard(record, idCard);
        // 身份证地址码未能匹配到省市时，降级到手机号号段
        fillMissingProvinceAndCityByMobile(record);
        if (idCard.length() == 18) {
            // 18 位身份证第 17 位为性别顺序码，奇数男、偶数女
            setGender(record, idCard.charAt(16));
        } else if (idCard.length() == 15) {
            // 15 位身份证第 15 位为性别顺序码
            setGender(record, idCard.charAt(14));
        }
    }

    /**
     * 从身份证地址码（前 6 位）解析省份和城市名称并写入 record。
     * <p>
     * 直辖市（京津沪渝）无独立地市级行政区，城市名与省份名相同。
     * </p>
     */
    private void fillProvinceAndCityByIdCard(Visitor record, String idCard) {
        // 1. 身份证前 6 位是行政区划地址码，不足 6 位无法解析
        if (idCard.length() < 6) {
            return;
        }
        // 2. 截取前 6 位原始区划码
        String addressCode = idCard.substring(0, 6);
        // 3. 取前 2 位（省份标识）并补全 `0000` 获得省级行政区划码
        String provinceCode = addressCode.substring(0, 2) + "0000";
        // 4. 取前 4 位（地市标识）并补全 `00` 获得市级行政区划码
        String cityCode = addressCode.substring(0, 4) + "00";
        
        // 5. 从区划表中查询对应的省级和地市级中文行政区划名
        AdministrativeDivision province = getAdministrativeDivision(provinceCode);
        AdministrativeDivision city = getAdministrativeDivision(cityCode);
        
        // 6. 回填省份名称
        if (province != null && StringUtils.isNotBlank(province.getName())) {
            record.setProvince(province.getName());
        }
        // 7. 回填地市名称：
        if (city != null && StringUtils.isNotBlank(city.getName())) {
            record.setCity(city.getName());
        } else if (province != null && isMunicipality(province.getName())) {
            // 7.1 特殊逻辑：若是直辖市（北京、天津、上海、重庆），其地址码第三四位无法直接查出地级市，
            // 此时地市名称直接取省级行政区划名称（如：北京市 - 北京市）
            record.setCity(province.getName());
        }
    }

    /**
     * 当省或市字段仍为空时，用手机号前 7 位号段补充省市信息（仅补空缺字段）。
     */
    private void fillMissingProvinceAndCityByMobile(Visitor record) {
        String mobile = record.getMobile();
        if (StringUtils.isBlank(mobile) || mobile.length() < 7) {
            return;
        }
        QueryWrapper<MobileNumberSegment> ew = new QueryWrapper<>();
        ew.eq(MobileNumberSegment.SEGMENT, mobile.substring(0, 7));
        MobileNumberSegment mobileNumberSegment = mobileNumberSegmentService.getOne(ew);
        if (mobileNumberSegment == null) {
            return;
        }
        if (StringUtils.isBlank(record.getProvince()) && StringUtils.isNotBlank(mobileNumberSegment.getProvince())) {
            record.setProvince(mobileNumberSegment.getProvince());
        }
        if (StringUtils.isBlank(record.getCity()) && StringUtils.isNotBlank(mobileNumberSegment.getCity())) {
            record.setCity(mobileNumberSegment.getCity());
        }
    }

    /**
     * 根据身份证性别顺序码（奇数男、偶数女）设置 gender 字段。
     *
     * @param genderCode 身份证性别顺序码字符
     */
    private void setGender(Visitor record, char genderCode) {
        if (genderCode >= '0' && genderCode <= '9') {
            record.setGender((genderCode - '0') % 2 == 1 ? Visitor.GENDER_MALE : Visitor.GENDER_FEMALE);
        }
    }

    /**
     * 无身份证时，直接用手机号前 7 位号段查询并写入省市信息。
     */
    private void fillProvinceAndCityByMobile(Visitor record) {
        String mobile = record.getMobile();
        if (StringUtils.isBlank(mobile) || mobile.length() < 7) {
            return;
        }
        QueryWrapper<MobileNumberSegment> ew = new QueryWrapper<>();
        ew.eq(MobileNumberSegment.SEGMENT, mobile.substring(0, 7));
        MobileNumberSegment mobileNumberSegment = mobileNumberSegmentService.getOne(ew);
        if (mobileNumberSegment != null && StringUtils.isNotBlank(mobileNumberSegment.getProvince())) {
            record.setProvince(mobileNumberSegment.getProvince());
        }
        if (mobileNumberSegment != null && StringUtils.isNotBlank(mobileNumberSegment.getCity())) {
            record.setCity(mobileNumberSegment.getCity());
        }
    }

    /**
     * 根据行政区划代码查询行政区划信息。
     *
     * @param code 行政区划代码（如 "110000" 代表北京市）
     */
    private AdministrativeDivision getAdministrativeDivision(String code) {
        QueryWrapper<AdministrativeDivision> ew = new QueryWrapper<>();
        ew.eq(AdministrativeDivision.CODE, code);
        return administrativeDivisionService.getOne(ew);
    }

    /**
     * 判断省份是否为直辖市（北京、天津、上海、重庆）。
     * 直辖市无独立地市级行政区，解析城市时城市名与省份名相同。
     */
    private boolean isMunicipality(String provinceName) {
        return "北京市".equals(provinceName) || "天津市".equals(provinceName)
                || "上海市".equals(provinceName) || "重庆市".equals(provinceName);
    }

    /**
     * 为 ExcelReader 注册表头别名，支持中英文表头混用。
     * <p>
     * 映射关系：姓名/name → name，手机号/mobile → mobile，身份证号/身份证/idCard → idCard
     * </p>
     */
    private void addHeaderAlias(ExcelReader reader) {
        reader.addHeaderAlias("姓名", "name");
        reader.addHeaderAlias("name", "name");
        reader.addHeaderAlias("手机号", "mobile");
        reader.addHeaderAlias("mobile", "mobile");
        reader.addHeaderAlias("身份证号", "idCard");
        reader.addHeaderAlias("身份证", "idCard");
        reader.addHeaderAlias("idCard", "idCard");
    }

    /**
     * 校验 Excel 表头：必须且只能包含"姓名、手机号、身份证号"三列（不区分中英文，顺序不限）。
     * <p>
     * 空白列会被忽略；出现未知列或列数不足时均报错。
     * </p>
     */
    private void validateImportHeaders(List<Object> headers) {
        if (headers == null || headers.isEmpty()) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "Excel表头不能为空");
        }

        int headerCount = 0;
        Set<String> actualFields = new LinkedHashSet<>();
        for (Object header : headers) {
            if (header == null || StringUtils.isBlank(header.toString())) {
                continue;
            }
            headerCount++;
            String fieldName = convertHeaderToFieldName(header.toString().trim());
            if (fieldName == null) {
                throw new MyException(HttpStatus.SC_BAD_REQUEST, "Excel只能包含手机号、身份证号、姓名");
            }
            actualFields.add(fieldName);
        }

        if (headerCount != IMPORT_REQUIRED_FIELDS.size() || !IMPORT_REQUIRED_FIELDS.equals(actualFields)) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "Excel必须且只能包含手机号、身份证号、姓名");
        }
    }

    /**
     * 将 Excel 表头文字转换为对应的实体字段名，不匹配时返回 null。
     */
    private String convertHeaderToFieldName(String header) {
        if ("手机号".equals(header) || "mobile".equals(header)) {
            return "mobile";
        }
        if ("身份证号".equals(header) || "身份证".equals(header) || "idCard".equals(header)) {
            return "idCard";
        }
        if ("姓名".equals(header) || "name".equals(header)) {
            return "name";
        }
        return null;
    }
}
