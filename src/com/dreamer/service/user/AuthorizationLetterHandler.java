package com.dreamer.service.user;

import com.dreamer.domain.account.GoodsAccount;
import com.dreamer.domain.authorization.Authorization;
import com.dreamer.domain.goods.Goods;
import com.dreamer.domain.user.Agent;
import org.springframework.stereotype.Service;
import ps.mx.otter.exception.ApplicationException;
import ps.mx.otter.utils.date.DateUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Service
public class AuthorizationLetterHandler {

	public byte[] generateLetter(Agent agent, Authorization auth, Path path) {
		try {
			Goods goods=auth.getAuthorizationType().getGoods();
			GoodsAccount gac=agent.loadAccountForGoodsNotNull(goods);
			ByteArrayInputStream in = new ByteArrayInputStream(
					Files.readAllBytes(path));
			BufferedImage image = ImageIO.read(in);
			BufferedImage tag = new BufferedImage(614, 820,
					BufferedImage.TYPE_INT_ARGB);
			Graphics2D gp = (Graphics2D) tag.getGraphics();
			gp.drawImage(image, 0, 0, null);
			//gp.setBackground(Color.WHITE);
			gp.setColor(Color.BLACK);
			gp.setFont(new Font("宋体", Font.BOLD, 18));
			gp.drawString(agent.getRealName(), 202, 398);
			gp.drawString(replacePrivacy(agent.getWeixin()), 375, 398);
//			gp.drawString(replacePrivacy(agent.getMobile()), 250, 507);
			gp.drawString(replacePrivacy(agent.getIdCard()), 230, 431);
			gp.drawString(agent.getAgentCode(), 282, 465);
//			gp.drawString(goods.getName(), 235, 607);//
			String an=goods.getAuthorizationType().getName();
//			gp.drawString(an, 270-an.length()*9, 560);
			gp.setFont(new Font("宋体", Font.BOLD, 20));
			gp.drawString(gac.getAgentLevel().getName(), 270-gac.getAgentLevel().getName().length()*10, 560);
			gp.setFont(new Font("宋体", Font.BOLD, 18));
			gp.drawString(DateUtil.date2string(auth.getUpdateTime(),DateUtil.DATE_FORMAT), 165, 666);

			gp.dispose();
			ByteArrayOutputStream out=new ByteArrayOutputStream();
			ImageIO.write(tag,"png",out);
			return out.toByteArray();
		} catch (Exception exp) {
			exp.printStackTrace();
			throw new ApplicationException("生成授权证书失败");
		}
	}
	
	private String replacePrivacy(String data){
		if(Objects.isNull(data)){
			return "";
		}
		if(data.length()>4){
			return data.substring(0, data.length()-4)+PRIVACY;
		}else{
			return PRIVACY;
		}
	}

	private final static String PRIVACY="****";
}
