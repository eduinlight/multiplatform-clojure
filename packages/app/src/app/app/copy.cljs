(ns app.app.copy)

(def strings
  {:app/loading "Loading…"
   :auth/login-title "Welcome back"
   :auth/register-title "Create account"
   :auth/login-submit "Sign in"
   :auth/register-submit "Sign up"
   :auth/submitting "Working…"
   :auth/to-register "I need an account"
   :auth/to-login "I already have an account"
   :field/name "Name"
   :field/name-placeholder "Ada Lovelace"
   :field/email "Email"
   :field/email-placeholder "you@example.com"
   :field/password "Password"
   :field/password-placeholder "at least 8 characters"
   :todos/sign-out "Sign out"
   :todos/draft-placeholder "What needs doing?"
   :todos/add "Add"
   :todos/empty "Nothing here yet."
   :todos/delete "Delete"})

(defn t [k]
  (get strings k (name k)))
