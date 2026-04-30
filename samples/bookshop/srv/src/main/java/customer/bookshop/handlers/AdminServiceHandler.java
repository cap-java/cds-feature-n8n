package customer.bookshop.handlers;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

import cds.gen.adminservice.AdminService_;
import cds.gen.adminservice.ConfirmBookCreationContext;

@Component
@ServiceName(AdminService_.CDS_NAME)
public class AdminServiceHandler implements EventHandler {

	private static final Logger log = LoggerFactory.getLogger(AdminServiceHandler.class);

	@On
	public void confirmBookCreation(ConfirmBookCreationContext context) {
		ConfirmBookCreationContext.ReturnType result = ConfirmBookCreationContext.ReturnType.create();
		result.setBookId("BOOK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
		log.info("Book creation confirmed for book {} with stock {}", context.getBook(), context.getStock());
		context.setResult(result);
	}

}
