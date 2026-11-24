import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { adminAddress } from './admin-address'
import './landing.css'

// The platform's own page at the site root. Shop owners learn what it does and go to the admin to
// sign up; a customer who only knows a shop's web name can jump to that shop.
// The pictures on the right are drawn with plain HTML: a shop, an M-Pesa prompt and a paid order,
// which is exactly what the platform does.

export function LandingPage() {
  const admin = adminAddress()
  const signUpLink = admin + '/signup'
  const logInLink = admin + '/login'

  return (
    <div className="landing">
      <header className="landing-nav">
        <a href="/" className="wordmark">
          SME Commerce
        </a>
        <nav aria-label="Sections">
          <a href="#how">How it works</a>
          <a href="#features">Features</a>
          <a href="#customers">For customers</a>
        </nav>
        <div className="landing-nav-actions">
          <a href={logInLink} className="landing-login">
            Log in
          </a>
          <a href={signUpLink} className="primary-button landing-nav-cta">
            Open your shop
          </a>
        </div>
      </header>

      <main className="landing-main">
        <section className="hero">
          <div className="hero-text">
            <p className="eyebrow">For small shops in Kenya</p>
            <h1>
              Your shop online. Paid by <em>M-Pesa</em>.
            </h1>
            <p className="hero-lead">
              Add your products, share one link, and let customers order without an account. They pay with
              M-Pesa or card, and the order turns Paid on its own, for them and for you.
            </p>
            <div className="hero-actions">
              <a href={signUpLink} className="primary-button">
                Open your shop
              </a>
              <a href="#how" className="secondary-button">
                See how it works
              </a>
            </div>
            <ul className="hero-facts">
              <li>M-Pesa STK push</li>
              <li>Cards through Stripe</li>
              <li>Prices in KES</li>
            </ul>
          </div>

          <div className="hero-visual" aria-hidden="true">
            <ShopPicture />
            <MpesaPicture />
            <PaidPicture />
          </div>
        </section>

        <section id="how" className="landing-section">
          <div className="section-heading">
            <p className="eyebrow">How it works</p>
            <h2>From sign-up to your first sale in three steps.</h2>
          </div>
          <ol className="steps">
            <li>
              <span className="step-number">01</span>
              <h3>Sign up your shop</h3>
              <p>Pick a name and a web name. Your shop gets its own address, like /s/kamau-hardware.</p>
            </li>
            <li>
              <span className="step-number">02</span>
              <h3>Add products and stock</h3>
              <p>Photos, prices and categories. Stock is held when someone orders and released if they don't pay.</p>
            </li>
            <li>
              <span className="step-number">03</span>
              <h3>Share your link</h3>
              <p>Post it on WhatsApp or Instagram. Customers order with just a name and a phone number.</p>
            </li>
          </ol>
        </section>

        <section id="features" className="landing-section">
          <div className="section-heading">
            <p className="eyebrow">What you get</p>
            <h2>Built around how Kenyan customers pay.</h2>
          </div>
          <div className="features">
            <article className="feature feature-wide">
              <h3>M-Pesa and card, one checkout</h3>
              <p>
                The customer gets the M-Pesa prompt on their phone and enters their PIN. Cards go through Stripe.
                If Safaricom's answer never comes, the payment is checked again, and a customer is never told it
                failed when nobody knows yet.
              </p>
            </article>
            <article className="feature feature-dark">
              <h3>Orders, live</h3>
              <p>New orders appear in your admin as they happen, and turn Paid without a refresh.</p>
            </article>
            <article className="feature">
              <h3>Stock that adds up</h3>
              <p>Every change is kept in a history: what was sold, restocked or corrected, and by whom.</p>
            </article>
            <article className="feature feature-wide feature-assistant">
              <div>
                <h3>An assistant that knows your shop</h3>
                <p>
                  Upload your delivery and returns policy. The assistant answers customers from your text only, and
                  says when it doesn't know.
                </p>
              </div>
              <div className="chat-sample" aria-hidden="true">
                <p className="chat-question">Do you deliver to Kisumu?</p>
                <p className="chat-answer">
                  Yes. Delivery outside Nairobi is KES 300 and takes two to three days.
                  <span>From: Delivery and returns</span>
                </p>
              </div>
            </article>
          </div>
        </section>

        <section id="customers" className="landing-section customers">
          <div>
            <p className="eyebrow">For customers</p>
            <h2>Looking for a shop?</h2>
            <p>Every shop has its own address. Type the shop's web name from the link they sent you.</p>
          </div>
          <FindShopForm />
        </section>

        <section className="closing">
          <h2>Ready when your customers are.</h2>
          <p>Sign up, add one product, and send your link to a friend to try it.</p>
          <a href={signUpLink} className="primary-button closing-button">
            Open your shop
          </a>
        </section>
      </main>

      <footer className="landing-footer">
        <span className="wordmark">SME Commerce</span>
        <span>Online shops for small Kenyan businesses.</span>
        <a href={logInLink}>Shop owner log in</a>
      </footer>
    </div>
  )
}

// "Type a shop's web name, go to its page": /s/<web name>.
function FindShopForm() {
  const navigate = useNavigate()
  const [webName, setWebName] = useState('')

  function goToShop(event: FormEvent) {
    event.preventDefault()
    const cleaned = webName.trim().toLowerCase()
    if (cleaned === '') {
      return
    }
    navigate('/s/' + encodeURIComponent(cleaned))
  }

  return (
    <form className="find-shop" onSubmit={goToShop}>
      <label htmlFor="shop-web-name">Shop's web name</label>
      <div className="find-shop-row">
        <span className="find-shop-prefix">/s/</span>
        <input
          id="shop-web-name"
          value={webName}
          onChange={(event) => setWebName(event.target.value)}
          placeholder="kamau-hardware"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
        />
        <button type="submit" className="primary-button">
          Go
        </button>
      </div>
    </form>
  )
}

// --- the pictures in the hero ---------------------------------------------------------------------

function ShopPicture() {
  return (
    <div className="picture picture-shop">
      <div className="picture-shop-bar">
        <span>Kamau Hardware</span>
        <span className="picture-pill">Cart 2</span>
      </div>
      <div className="picture-products">
        <div className="picture-product">
          <span className="picture-swatch swatch-1" />
          <span>Claw hammer</span>
          <strong>KES 850</strong>
        </div>
        <div className="picture-product">
          <span className="picture-swatch swatch-2" />
          <span>Wall paint, 4 L</span>
          <strong>KES 2,400</strong>
        </div>
        <div className="picture-product">
          <span className="picture-swatch swatch-3" />
          <span>Padlock</span>
          <strong>KES 400</strong>
        </div>
        <div className="picture-product">
          <span className="picture-swatch swatch-4" />
          <span>Tape measure</span>
          <strong>KES 350</strong>
        </div>
      </div>
    </div>
  )
}

function MpesaPicture() {
  return (
    <div className="picture picture-mpesa">
      <span className="picture-label">M-PESA</span>
      <p>Do you want to pay KES 1,250 to KAMAU HARDWARE?</p>
      <span className="picture-pin">Enter M-PESA PIN</span>
      <span className="picture-dots">
        <i />
        <i />
        <i />
        <i />
      </span>
    </div>
  )
}

function PaidPicture() {
  return (
    <div className="picture picture-paid">
      <span className="picture-label">Order KH-000214</span>
      <strong>Paid</strong>
      <span>The customer and the shop both see it, live.</span>
    </div>
  )
}
