package com.efinanceiro.servico;

import com.cloudinary.Cloudinary;
import com.efinanceiro.excecao.ServicoExternoIndisponivelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * Única classe que conhece o Cloudinary. Cada usuário tem um identificador fixo de foto
 * (efinanceiro/usuarios/{id}): enviar uma foto nova sobrescreve a anterior, então nunca sobra
 * foto órfã por troca.
 */
@Slf4j
@Service
public class ServicoFotoPerfil {

    private final Cloudinary cloudinary;

    public ServicoFotoPerfil(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Envia a foto de perfil do usuário, já recortada no servidor do Cloudinary em 512x512
     * centralizada no rosto.
     *
     * @param usuarioId Id do usuário dono da foto
     * @param conteudo Bytes da imagem (JPG, PNG ou WEBP, já validados)
     * @return URL segura da foto (com a versão, o que evita cache da foto antiga)
     * @throws ServicoExternoIndisponivelException se o Cloudinary recusar ou não responder
     */
    public String enviar(Long usuarioId, byte[] conteudo) {
        String url;

        try {
            Map<String, Object> opcoes = Map.of(
                    "public_id", idPublico(usuarioId),
                    "overwrite", true,
                    "invalidate", true,
                    "resource_type", "image",
                    "allowed_formats", "jpg,png,webp",
                    "transformation", "c_fill,g_face,w_512,h_512"
            );

            Map<?, ?> resultado = cloudinary.uploader().upload(conteudo, opcoes);

            url = (String) resultado.get("secure_url");
        } catch (IOException | RuntimeException e) {
            log.error("Falha ao enviar a foto de perfil do usuário {} ao Cloudinary", usuarioId, e);
            throw new ServicoExternoIndisponivelException("Não foi possível salvar a foto agora. Tente de novo.");
        }

        if (url == null) {
            log.error("Cloudinary não devolveu a URL da foto de perfil do usuário {}", usuarioId);
            throw new ServicoExternoIndisponivelException("Não foi possível salvar a foto agora. Tente de novo.");
        }

        return url;
    }

    /**
     * Apaga a foto de perfil do usuário no Cloudinary.
     *
     * @param usuarioId Id do usuário dono da foto
     * @throws ServicoExternoIndisponivelException se o Cloudinary não responder
     */
    public void apagar(Long usuarioId) {
        try {
            Map<String, Object> opcoes = Map.of("invalidate", true);
            cloudinary.uploader().destroy(idPublico(usuarioId), opcoes);
        } catch (IOException | RuntimeException e) {
            log.error("Falha ao apagar a foto de perfil do usuário {} no Cloudinary", usuarioId, e);
            throw new ServicoExternoIndisponivelException("Não foi possível remover a foto agora. Tente de novo.");
        }
    }

    private String idPublico(Long usuarioId) {
        return "efinanceiro/usuarios/" + usuarioId;
    }
}
