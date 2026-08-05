package customer.bookshop.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

import cds.gen.adminservice.AdminService_;
import cds.gen.adminservice.Books;
import cds.gen.adminservice.ConfirmBookCreationContext;
import cds.gen.adminservice.ConfirmBookDeletionContext;
import sap.capire.n8n_plugin.services.N8nService;

@Component
@ServiceName(AdminService_.CDS_NAME)
public class AdminServiceHandler implements EventHandler {

	private static final Logger log = LoggerFactory.getLogger(AdminServiceHandler.class);

	@Autowired
	private N8nService n8nService;

	@On
	public void confirmBookCreation(ConfirmBookCreationContext context) {
		log.info("Book creation confirmed for book {} with stock {}", context.getBook(), context.getStock());
		context.setResult(ConfirmBookCreationContext.ReturnType.create());
	}

	@On
	public void confirmBookDeletion(ConfirmBookDeletionContext context) {
		log.info("Book deletion confirmed for book {} with author {}", context.getBook(), context.getAuthor());
		context.setResult(ConfirmBookDeletionContext.ReturnType.create());
	}

	@After(event = CqnService.EVENT_CREATE, entity = "AdminService.Books")
	public void afterCreateBook(List<Books> books) {
		books.forEach(book -> {
			Map<String, Object> payload = new HashMap<>();
			payload.put("ID", book.getId());
			payload.put("title", book.getTitle());
			payload.put("stock", book.getStock());
			n8nService.trigger("book-created", payload);
		});

	}
}
