from __future__ import annotations

import unittest

from soc_hunt.auth import AuthConfig, bearer_token, parse_tokens


class AuthConfigTest(unittest.TestCase):
    def test_bearer_token_parsing(self) -> None:
        self.assertEqual(bearer_token("Bearer abc123"), "abc123")
        self.assertEqual(bearer_token("bearer abc123"), "abc123")
        self.assertEqual(bearer_token("Basic abc123"), "")
        self.assertEqual(bearer_token(None), "")

    def test_required_mode_enforces_role_hierarchy(self) -> None:
        auth = AuthConfig(
            mode="required",
            token_spec="view-token:viewer:alice,analyst-token:analyst:bob,lead-token:lead:carol,admin-token:admin:dana",
        )

        viewer = auth.authenticate("Bearer view-token")
        analyst = auth.authenticate("Bearer analyst-token")
        lead = auth.authenticate("Bearer lead-token")
        admin = auth.authenticate("Bearer admin-token")

        self.assertIsNotNone(viewer)
        self.assertTrue(auth.authorize(viewer, "viewer"))
        self.assertFalse(auth.authorize(viewer, "analyst"))
        self.assertTrue(auth.authorize(analyst, "analyst"))
        self.assertFalse(auth.authorize(analyst, "lead"))
        self.assertTrue(auth.authorize(lead, "lead"))
        self.assertFalse(auth.authorize(lead, "admin"))
        self.assertTrue(auth.authorize(admin, "admin"))
        self.assertIsNone(auth.authenticate("Bearer missing"))

    def test_off_mode_allows_local_admin(self) -> None:
        auth = AuthConfig(mode="off", token_spec="")
        principal = auth.authenticate(None)
        self.assertIsNotNone(principal)
        self.assertFalse(principal.authenticated)
        self.assertEqual(principal.role, "admin")
        self.assertTrue(auth.authorize(principal, "admin"))

    def test_invalid_token_specs_are_ignored(self) -> None:
        tokens = parse_tokens("bad,token:unknown:bob,good:analyst:alice")
        self.assertEqual(set(tokens), {"good"})


if __name__ == "__main__":
    unittest.main()
