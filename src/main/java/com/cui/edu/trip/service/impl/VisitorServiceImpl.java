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

    private static final List<String> IMPORT_TEMPLATE_HEADERS = Arrays.asList("姓名", "手机号", "身份证号");

    private static final Set<String> IMPORT_REQUIRED_FIELDS = new HashSet<>(Arrays.asList("name", "mobile", "idCard"));

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private static final Pattern ID_CARD_15_PATTERN = Pattern.compile("^\\d{15}$");

    private static final Pattern ID_CARD_18_PATTERN = Pattern.compile("^\\d{17}[0-9Xx]$");

    private static final DateTimeFormatter STRICT_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    private static final int[] ID_CARD_WEIGHT = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    private static final char[] ID_CARD_CHECK_CODE = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    @Autowired
    private MuseumService museumService;

    @Autowired
    private MobileNumberSegmentService mobileNumberSegmentService;

    @Autowired
    private AdministrativeDivisionService administrativeDivisionService;

    @Override
    public void saveVisitor(Visitor record) {
        validateMobile(record.getMobile());
        validateIdCardFormat(record.getIdCard());
        validateIdCardByMuseumConfig(record);
        // 若 wechat_openid 已存在，则合并到已有记录（更新）而非报错
        mergeExistingVisitorByOpenid(record);
        // 身份证优先补省市和性别；身份证为空时，按手机号号段补省市，性别置为未知。
        fillProvinceCityAndGender(record);
        if (record.getId() == null && record.getIsDeleted() == null) {
            record.setIsDeleted(SysConstants.IS_FALSE);
        }
        if (record.getId() == null && restoreDeletedVisitor(record)) {
            return;
        }
        super.saveOrUpdate(record);
    }

    @Override
    public int importExcel(MultipartFile file, Long teamId, String batchNo) {
        if (file == null || file.isEmpty()) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "导入文件不能为空");
        }

        try (ExcelReader reader = ExcelUtil.getReader(file.getInputStream())) {
            validateImportHeaders(reader.readRow(0));
            addHeaderAlias(reader);
            List<Visitor> visitorList = reader.readAll(Visitor.class);
            if (visitorList == null || visitorList.isEmpty()) {
                return 0;
            }
            for (Visitor visitor : visitorList) {
                visitor.setId(null);
                visitor.setTeamId(teamId);
                visitor.setBatchNo(batchNo);
                visitor.setIsDeleted(SysConstants.IS_FALSE);
                validateMobile(visitor.getMobile());
                validateIdCardFormat(visitor.getIdCard());
                // Excel导入沿用同一套游客信息补齐规则。
                fillProvinceCityAndGender(visitor);
            }
            super.saveBatch(visitorList);
            return visitorList.size();
        } catch (IOException e) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "Excel导入失败");
        }
    }

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

    @Override
    public Visitor findByWechatOpenid(String wechatOpenid) {
        QueryWrapper<Visitor> ew = new QueryWrapper<>();
        ew.eq(Visitor.WECHAT_OPENID, wechatOpenid);
        ew.eq(Visitor.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(Visitor.ID);
        ew.last("limit 1");
        return super.getOne(ew);
    }

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

    /**
     * 若 wechat_openid 已绑定另一条未删除记录，则将该记录的 id 赋给 record，
     * 使后续 saveOrUpdate 执行 UPDATE 而不是 INSERT，避免重复 openid 报错。
     */
    private void mergeExistingVisitorByOpenid(Visitor record) {
        if (StringUtils.isBlank(record.getWechatOpenid())) {
            return;
        }
        QueryWrapper<Visitor> ew = new QueryWrapper<>();
        ew.eq(Visitor.WECHAT_OPENID, record.getWechatOpenid());
        ew.eq(Visitor.IS_DELETED, SysConstants.IS_FALSE);
        if (record.getId() != null) {
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

    private Visitor findDeletedByWechatOpenid(String wechatOpenid) {
        QueryWrapper<Visitor> ew = new QueryWrapper<>();
        ew.eq(Visitor.WECHAT_OPENID, wechatOpenid);
        ew.eq(Visitor.IS_DELETED, SysConstants.IS_TRUE);
        ew.orderByDesc(Visitor.ID);
        ew.last("limit 1");
        return super.getOne(ew);
    }

    private void validateMobile(String mobile) {
        if (StringUtils.isBlank(mobile)) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "手机号不能为空");
        }
        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "手机号格式不正确");
        }
    }

    private void validateIdCardFormat(String idCard) {
        if (StringUtils.isBlank(idCard)) {
            return;
        }
        if (ID_CARD_15_PATTERN.matcher(idCard).matches()) {
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

    private void validateIdCardBirthDate(String birthDate) {
        try {
            LocalDate.parse(birthDate, STRICT_DATE_FORMATTER);
        } catch (Exception e) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "身份证号出生日期不正确");
        }
    }

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

    private void validateIdCardAddressCode(String addressCode) {
        String provinceCode = addressCode.substring(0, 2) + "0000";
        if (getAdministrativeDivision(provinceCode) == null) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "身份证号地址码不正确");
        }
    }

    private void validateIdCardByMuseumConfig(Visitor record) {
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

    private void fillProvinceCityAndGender(Visitor record) {
        String idCard = record.getIdCard();
        if (StringUtils.isBlank(idCard)) {
            fillProvinceAndCityByMobile(record);
            record.setGender(Visitor.GENDER_UNKNOWN);
            return;
        }
        fillProvinceAndCityByIdCard(record, idCard);
        fillMissingProvinceAndCityByMobile(record);
        if (idCard.length() == 18) {
            // 18位身份证第17位为性别顺序码，奇数男、偶数女。
            setGender(record, idCard.charAt(16));
        } else if (idCard.length() == 15) {
            // 15位身份证第15位为性别顺序码。
            setGender(record, idCard.charAt(14));
        }
    }

    private void fillProvinceAndCityByIdCard(Visitor record, String idCard) {
        if (idCard.length() < 6) {
            return;
        }
        String addressCode = idCard.substring(0, 6);
        String provinceCode = addressCode.substring(0, 2) + "0000";
        String cityCode = addressCode.substring(0, 4) + "00";
        AdministrativeDivision province = getAdministrativeDivision(provinceCode);
        AdministrativeDivision city = getAdministrativeDivision(cityCode);
        if (province != null && StringUtils.isNotBlank(province.getName())) {
            record.setProvince(province.getName());
        }
        if (city != null && StringUtils.isNotBlank(city.getName())) {
            record.setCity(city.getName());
        } else if (province != null && isMunicipality(province.getName())) {
            record.setCity(province.getName());
        }
    }

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

    private void setGender(Visitor record, char genderCode) {
        if (genderCode >= '0' && genderCode <= '9') {
            record.setGender((genderCode - '0') % 2 == 1 ? Visitor.GENDER_MALE : Visitor.GENDER_FEMALE);
        }
    }

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

    private AdministrativeDivision getAdministrativeDivision(String code) {
        QueryWrapper<AdministrativeDivision> ew = new QueryWrapper<>();
        ew.eq(AdministrativeDivision.CODE, code);
        return administrativeDivisionService.getOne(ew);
    }

    private boolean isMunicipality(String provinceName) {
        return "北京市".equals(provinceName) || "天津市".equals(provinceName)
                || "上海市".equals(provinceName) || "重庆市".equals(provinceName);
    }

    private void addHeaderAlias(ExcelReader reader) {
        reader.addHeaderAlias("姓名", "name");
        reader.addHeaderAlias("name", "name");
        reader.addHeaderAlias("手机号", "mobile");
        reader.addHeaderAlias("mobile", "mobile");
        reader.addHeaderAlias("身份证号", "idCard");
        reader.addHeaderAlias("身份证", "idCard");
        reader.addHeaderAlias("idCard", "idCard");
    }

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
