package com.efinanceiro.configuracao;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class ConfiguracaoCloudinary {

    /**
     * Cliente do Cloudinary configurado pela URL que o próprio painel do Cloudinary fornece
     * (cloudinary://chave:segredo@nome-da-nuvem). Sem a variável, o cliente sobe vazio: a aplicação
     * funciona normalmente e só o envio de foto falha (502), em vez de derrubar a inicialização.
     *
     * @param url Valor de CLOUDINARY_URL
     * @return Cliente do Cloudinary
     */
    @Bean
    public Cloudinary cloudinary(@Value("${app.cloudinary.url}") String url) {
        return url.isBlank() ? new Cloudinary(Map.of()) : new Cloudinary(url);
    }
}
