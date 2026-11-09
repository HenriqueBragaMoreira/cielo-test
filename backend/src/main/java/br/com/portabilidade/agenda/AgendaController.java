package br.com.portabilidade.agenda;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class AgendaController {

	private final AgendaService service;

	AgendaController(AgendaService service) {
		this.service = service;
	}

	/** Parcelas futuras do beneficiário; {@code 403} se ele não tem autorização registrada na plataforma. */
	@GetMapping("/api/beneficiarios/{beneficiarioId}/agenda")
	List<AgendaService.ParcelaAgenda> consultar(@PathVariable @NotBlank @Size(max = 100) String beneficiarioId) {
		return service.consultar(beneficiarioId);
	}

}
