package com.hfut.laboratory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfut.laboratory.enums.ReturnCode;
import com.hfut.laboratory.pojo.CouponCard;
import com.hfut.laboratory.pojo.CouponCardDetail;
import com.hfut.laboratory.pojo.Project;
import com.hfut.laboratory.pojo.RecordBusiness;
import com.hfut.laboratory.service.CouponCardDetailService;
import com.hfut.laboratory.service.CouponCardService;
import com.hfut.laboratory.service.ProjectService;
import com.hfut.laboratory.service.RecordBusinessService;
import com.hfut.laboratory.util.QueryWapperUtils;
import com.hfut.laboratory.vo.ApiResponse;
import com.hfut.laboratory.vo.card.CardDetailVo;
import com.hfut.laboratory.vo.card.CardSimple;
import com.hfut.laboratory.vo.card.CouponCardVo;
import com.hfut.laboratory.vo.PageResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author yzx
 * @since 2019-11-06
 */
@RestController
@RequestMapping("card")
@Api(tags = "优惠卡相关接口")
@Slf4j
public class CouponCardController {

    @Autowired
    private CouponCardService couponCardService;

    @Autowired
    private CouponCardDetailService couponCardDetailService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private RecordBusinessService recordBusinessService;


    @GetMapping("/list")
    @ApiOperation("获取优惠卡列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "current",value = "当前页"),
            @ApiImplicitParam(name = "size",value = "需要数据的条数limit")
    })
    @Cacheable(value = "getCouponCardList",keyGenerator="simpleKeyGenerator")
    public ApiResponse getCouponCardList(@RequestParam(required = false,defaultValue = "1") Integer current,
                                                                 @RequestParam(required = false,defaultValue = "20") Integer size){
        Page<CouponCard> page=new Page<>(current,size);
        IPage<CouponCard> cardIPage = couponCardService.page(page, null);
        return ApiResponse.ok(new PageResult<>(cardIPage.getRecords(),cardIPage.getTotal(),cardIPage.getSize()));
    }

    @GetMapping("/simple/list")
    @ApiOperation("获取收费项目列表id、name列表")
    @Cacheable(value = "getProjectSimpleList",keyGenerator="simpleKeyGenerator")
    public ApiResponse getProjectSimpleList(){
        List<CardSimple> res=new ArrayList<>();
        couponCardService.list(QueryWapperUtils.getInWapper("status",1))
                .forEach(card -> res.add(new CardSimple(((CouponCard)card).getId(),((CouponCard)card).getName())));
        return ApiResponse.ok(res);
    }

    @GetMapping("/c_d/list")
    @ApiOperation("获取优惠卡及对应项目的列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "current",value = "当前页"),
            @ApiImplicitParam(name = "size",value = "需要数据的条数limit")
    })
    @Cacheable(value = "getCouponCardAndDetailList",keyGenerator="simpleKeyGenerator")
    public ApiResponse getCouponCardAndDetailList(@RequestParam(required = false,defaultValue = "1") Integer current,
                                                                            @RequestParam(required = false,defaultValue = "20") Integer size){
        List<CouponCardVo> res=new ArrayList<>();
        Page<CouponCard> page=new Page<>(current,size);
        IPage<CouponCard> couponCardIPage = couponCardService.page(page, null);

        couponCardIPage.getRecords().forEach(card -> res.add(getCardDetailVo(card)));

        return ApiResponse.ok(new PageResult<>(res,couponCardIPage.getTotal(),couponCardIPage.getSize()));
    }

    @GetMapping
    @ApiOperation("通过条件查询优惠卡")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "current",value = "当前页"),
            @ApiImplicitParam(name = "size",value = "需要数据的条数limit"),
            @ApiImplicitParam(name = "projectId",value = "项目id"),
            @ApiImplicitParam(name = "status",value = "状态"),
            @ApiImplicitParam(name = "startTime",value = "起始时间"),
            @ApiImplicitParam(name = "endTime",value = "结束时间")
    })
    public ApiResponse queryCardList(@RequestParam(required = false,defaultValue = "1") Integer current,
                                                               @RequestParam(required = false,defaultValue = "20") Integer size,
                                                               @RequestParam(required = false) String name,
                                                               @RequestParam(required = false) Integer projectId,
                                                               @RequestParam(required = false) Integer status,
                                                               @RequestParam(required = false) LocalDateTime startTime,
                                                               @RequestParam(required = false) LocalDateTime endTime){
        QueryWrapper<CouponCard> queryWrapper=new QueryWrapper<>();

        List<Integer> cardIdList=new ArrayList<>();
        if(projectId!=null){
            couponCardDetailService.list(QueryWapperUtils.getInWapper("project_id", projectId))
                    .forEach(c_p->cardIdList.add(((CouponCardDetail)c_p).getCardId()));
            if(cardIdList.size()==0){
                return ApiResponse.ok(null);
            }
            queryWrapper.and(wapper->wapper.in("id",cardIdList.toArray()));
        }

        if(status!=null){
            queryWrapper.and(wapper->wapper.in("status",status));
        }
        if(startTime!=null){
            queryWrapper.and(wapper->wapper.ge("start_time",startTime));
        }
        if(endTime!=null){
            queryWrapper.and(wapper->wapper.le("end_time",endTime));
        }
        if(StringUtils.isNoneBlank(name)){
            queryWrapper.and(wapper->wapper.like("name","%"+name+"%"));
        }

        Page<CouponCard> page=new Page<>(current,size);
        IPage<CouponCard> couponCardIPage = couponCardService.page(page, queryWrapper);

        List<CouponCardVo> res=new ArrayList<>();
        couponCardIPage.getRecords().forEach(card -> res.add(getCardDetailVo(card)));
        return ApiResponse.ok(new PageResult<>(res,couponCardIPage.getTotal(),couponCardIPage.getSize()));
    }

    @GetMapping("/{id}")
    @ApiOperation("通过id获取优惠卡")
    @ApiImplicitParam(name = "id",value = "优惠卡的id")
    @Cacheable(value = "getCouponCardById",keyGenerator="simpleKeyGenerator")
    public ApiResponse getCouponCardById(@PathVariable Integer id){
        CouponCard card = couponCardService.getById(id);
        return ApiResponse.ok(card);
    }

    @GetMapping("/c_d/{id}")
    @ApiOperation("通过id获取优惠卡及对应项目")
    @ApiImplicitParam(name = "id",value = "优惠卡的id")
    @Cacheable(value = "getCouponCardAndDetailById",keyGenerator="simpleKeyGenerator")
    public ApiResponse getCouponCardAndDetailById(@PathVariable Integer id){
        CouponCard card = couponCardService.getById(id);
        return ApiResponse.ok(getCardDetailVo(card));
    }


    @PostMapping("/freeze/{id}")
    @ApiOperation("冻结优惠卡 需要权限[card_freeze]")
    public ApiResponse freezeCard(@PathVariable Integer id){
        CouponCard card=couponCardService.getById(id);
        if(card==null){
            return ApiResponse.selfError(ReturnCode.CARD_NOT_EXIST);
        }
        card.setStatus(card.getStatus()==1 ? 0 :1);
        boolean res=couponCardService.updateById(card);
        return res ? ApiResponse.ok() : ApiResponse.serverError();
    }

    @PostMapping("/add")
    @ApiOperation("添加优惠卡（需要权限：[card_add]）")
    @ApiImplicitParam(name = "card",value = "优惠卡的json对象")
    public ApiResponse insertCouponCard(@RequestBody CouponCard card){
        if(card.getName()==null || card.getStartTime()==null || card.getEndTime()==null || card.getPrice()==null){
            return ApiResponse.selfError(ReturnCode.NEED_PARAM);
        }
        card.setStatus(1);
        boolean res = couponCardService.save(card);
        return res ? ApiResponse.created():ApiResponse.serverError();
    }

    @PutMapping("/edit")
    @ApiOperation("修改优惠卡（需要权限：[card_edit]）")
    @ApiImplicitParam(name = "card",value = "优惠卡的json对象")
    public ApiResponse updateCouponCard(@RequestBody CouponCard card){
        if(card.getId()==null || card.getName()==null || card.getStartTime()==null || card.getEndTime()==null || card.getPrice()==null){
            return ApiResponse.selfError(ReturnCode.NEED_PARAM);
        }
        if(!isCardExist(card.getId())){
            return ApiResponse.selfError(ReturnCode.CARD_NOT_EXIST);
        }
        boolean res = couponCardService.updateById(card);
        return res ? ApiResponse.ok():ApiResponse.serverError();
    }


    @PutMapping("/edit_pro")
    @ApiOperation("修改优惠卡的项目（需要权限：[card_edit]）")
    @ApiImplicitParam(name = "cardDetailVo",value = "传递card_pro的项目信息")
    @Transactional
    public ApiResponse updateCouponCardDetail(@RequestBody CardDetailVo cardDetailVo){
        if(cardDetailVo.getCardId()==null || cardDetailVo.getProDetails()==null){
            return ApiResponse.selfError(ReturnCode.NEED_PARAM);
        }
        if(!isCardExist(cardDetailVo.getCardId())){
            return ApiResponse.selfError(ReturnCode.CARD_NOT_EXIST);
        }
        for(CardDetailVo.ProDetail detail:cardDetailVo.getProDetails()){
            if(detail.getTimes()==null){
                return ApiResponse.selfError(ReturnCode.NEED_PARAM);
            }
        }
        boolean res1=true,res2=true;

        res1=couponCardDetailService.remove(QueryWapperUtils.getInWapper("card_id",cardDetailVo.getCardId()));
        for(CardDetailVo.ProDetail pro:cardDetailVo.getProDetails()){
            CouponCardDetail couponCardDetail=CouponCardDetail.builder()
                    .cardId(cardDetailVo.getCardId())
                    .projectId(pro.getProjectId())
                    .introduction(pro.getIntroduction())
                    .times(pro.getTimes())
                    .build();
            if(!couponCardDetailService.save(couponCardDetail)){
                res2=false;
                break;
            }
        }

        if(res1 && res2){
            return ApiResponse.ok();
        }else {
            log.info(this.getClass().getName()+"updateCouponCardDetail:error");
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.serverError();
        }
    }

    @DeleteMapping("/del/{id}")
    @ApiOperation("删除优惠卡（需要权限：[card_del]）")
    @ApiImplicitParam(name = "id",value = "优惠卡的id")
    @Transactional
    public ApiResponse deleteCouponCard(@PathVariable Integer id){
        boolean res1=true,res2=true;

        QueryWrapper<RecordBusiness> queryWapper=new QueryWrapper<>();
        queryWapper.and(wapper->wapper.in("type",1))
                .and(wapper->wapper.in("thing_id",id));

        if(recordBusinessService.list(queryWapper).size()!=0){
            return ApiResponse.selfError(ReturnCode.DELETE_FALI_Foreign_KEY);
        }

        try{
            res1=couponCardDetailService.remove(QueryWapperUtils.getInWapper("card_id",id));
            res2 = couponCardService.removeById(id);
        }catch (Exception e){
            log.info(this.getClass().getName()+"deleteCouponCard:error");
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.selfError(ReturnCode.DELETE_FALI_Foreign_KEY);
        }

        if(res1 && res2){
            return ApiResponse.ok();
        } else {
            log.info(this.getClass().getName()+"deleteCouponCard:error");
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.serverError();
        }
    }

    private boolean isCardExist(Integer id) {
        return couponCardService.getById(id)!=null;
    }

    private CouponCardVo getCardDetailVo(CouponCard card){
        List<CouponCardVo.Deatil> deatils=new ArrayList<>();
        couponCardDetailService.list(QueryWapperUtils.getInWapper("card_id",card.getId())).forEach(d_p-> {
            CouponCardDetail couponCardDetail = (CouponCardDetail) d_p;
            Project project = projectService.getById(couponCardDetail.getProjectId());
            CouponCardVo.Deatil deatil = new CouponCardVo.Deatil(project.getId(),project.getName(), couponCardDetail.getTimes());
            deatils.add(deatil);
        });

         return new CouponCardVo(card,deatils);
    }


}
