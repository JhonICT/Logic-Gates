# Security Policy

## Reporting a security issue

Do not post passwords, API tokens, OAuth client secrets, service-account private keys, or signing keystores in public issues or pull requests. For now, contact the repository owner privately through the contact mechanism listed on their GitHub profile.

## Secret handling

- Keep release keystores/passwords in encrypted offline storage and GitHub Actions secrets.
- Do not commit real `android/app/google-services.json`; use the ignored local file and the `google-services.json.example` template.
- Do not embed Firebase service-account JSON, Facebook App Secret, Yahoo client secret, or other server credentials in the app.
- Review third-party GitHub Actions and dependency updates before accepting them.
- Public repository visibility exposes the source code to everyone.
