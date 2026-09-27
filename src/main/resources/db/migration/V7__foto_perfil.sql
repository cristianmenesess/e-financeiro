-- URL da foto de perfil no Cloudinary (secure_url, já com a versão). Nulo = sem foto.
ALTER TABLE usuarios ADD COLUMN foto_url VARCHAR(500);
