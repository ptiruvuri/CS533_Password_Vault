// AddEditFragment.java
// Fragment for adding a new contact or editing an existing one
package com.smd.passwordvault.fragments;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.AppCompatTextView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import com.smd.passwordvault.R;
import com.smd.passwordvault.activities.MainActivity;
import com.smd.passwordvault.helpers.EncryptionUtil;
import com.smd.passwordvault.helpers.GeneratePassword;
import com.smd.passwordvault.sql.DatabaseDescription;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;


public class AddEditFragment extends Fragment
   implements LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener  {

   private static final String TAG = "AddEditFragment";

   // defines callback method implemented by MainActivity
   public interface AddEditFragmentListener {
      // called when account is saved
      void onAddEditCompleted(Uri accountUri);
   }

   // constant used to identify the Loader
   private static final int CONTACT_LOADER = 0;

   private AddEditFragmentListener listener; // MainActivity
   private Uri accountUri; // Uri of selected account
   private boolean addingNewAccount = true; // adding (true) or editing

   // EditTexts for account information
   private TextInputLayout nameTextInputLayout;
   private TextInputLayout passwordTextInputLayout;
   private TextInputLayout suggestPasswordTextInputLayout;
   private FloatingActionButton saveAccountFAB;

   private AppCompatTextView textViewLinkSuggestPassword;

   private CoordinatorLayout coordinatorLayout; // used with SnackBars

   // set AddEditFragmentListener when Fragment attached
   @Override
   public void onAttach(Context context) {
      super.onAttach(context);
      listener = (AddEditFragmentListener) context;
   }

   // remove AddEditFragmentListener when Fragment detached
   @Override
   public void onDetach() {
      super.onDetach();
      listener = null;
   }

   // called when Fragment's view needs to be created
   @Override
   public View onCreateView(
      LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
      super.onCreateView(inflater, container, savedInstanceState);
      setHasOptionsMenu(true); // fragment has menu items to display

      // inflate GUI and get references to EditTexts
      View view =
         inflater.inflate(R.layout.fragment_add_edit, container, false);
      nameTextInputLayout =
         (TextInputLayout) view.findViewById(R.id.nameTextInputLayout);
      nameTextInputLayout.getEditText().addTextChangedListener(
         nameChangedListener);

      passwordTextInputLayout =
         (TextInputLayout) view.findViewById(R.id.passwordTextInputLayout);

      suggestPasswordTextInputLayout =
              (TextInputLayout) view.findViewById(R.id.suggestPasswordTextInputLayout);

      textViewLinkSuggestPassword = (AppCompatTextView) view.findViewById(R.id.textViewLinkSuggestPassword);

      textViewLinkSuggestPassword.setOnClickListener(this);

      // set FloatingActionButton's event listener
      saveAccountFAB = (FloatingActionButton) view.findViewById(
         R.id.saveFloatingActionButton);
      saveAccountFAB.setOnClickListener(saveAccountButtonClicked);
      updateSaveButtonFAB();

      // used to display SnackBars with brief messages
      coordinatorLayout = (CoordinatorLayout) getActivity().findViewById(
         R.id.coordinatorLayout);

      Bundle arguments = getArguments(); // null if creating new account

      if (arguments != null) {
         addingNewAccount = false;
         accountUri = arguments.getParcelable(MainActivity.ACCOUNT_URI);
      }

      // if editing an existing account, create Loader to get the account
      if (accountUri != null)
         getLoaderManager().initLoader(CONTACT_LOADER, null, this);

      return view;
   }

   // detects when the text in the nameTextInputLayout's EditText changes
   // to hide or show saveButtonFAB
   private final TextWatcher nameChangedListener = new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count,
         int after) {}

      // called when the text in nameTextInputLayout changes
      @Override
      public void onTextChanged(CharSequence s, int start, int before,
         int count) {
         updateSaveButtonFAB();
      }

      @Override
      public void afterTextChanged(Editable s) { }
   };

   // shows saveButtonFAB only if the name is not empty
   private void updateSaveButtonFAB() {
      String input =
         nameTextInputLayout.getEditText().getText().toString();

      // if there is a name for the account, show the FloatingActionButton
      if (input.trim().length() != 0)
         saveAccountFAB.show();
      else
         saveAccountFAB.hide();
   }

   // responds to event generated when user saves a account
   private final View.OnClickListener saveAccountButtonClicked =
      new View.OnClickListener() {
         @Override
         public void onClick(View v) {
            // hide the virtual keyboard
            ((InputMethodManager) getActivity().getSystemService(
               Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(
               getView().getWindowToken(), 0);
            saveAccount(); // save recipe to the database
         }
      };

   // saves account information to the database
   private void saveAccount() {
      // create ContentValues object containing account's key-value pairs
      ContentValues contentValues = new ContentValues();
      contentValues.put(DatabaseDescription.AccountData.COLUMN_NAME,
         nameTextInputLayout.getEditText().getText().toString());

      // encrypt the password before saving it to the database
      String origPwd =passwordTextInputLayout.getEditText().getText().toString();
      String encPwd = origPwd;
      try{
         encPwd = EncryptionUtil.encryptPassword(origPwd);
      }
      catch (Exception ex){
         Log.e(TAG,"Error while encrypting password for:" + nameTextInputLayout.getEditText().getText().toString());
      }
      contentValues.put(DatabaseDescription.AccountData.COLUMN_PASSWORD, encPwd);

      if (addingNewAccount) {
         // use Activity's ContentResolver to invoke
         // insert on the AccountContentProvider
         Uri newAccountUri = getActivity().getContentResolver().insert(
            DatabaseDescription.AccountData.CONTENT_URI, contentValues);

         if (newAccountUri != null) {
            Snackbar.make(coordinatorLayout,
               R.string.account_added, Snackbar.LENGTH_LONG).show();
            listener.onAddEditCompleted(newAccountUri);
         }
         else {
            Snackbar.make(coordinatorLayout,
               R.string.account_not_added, Snackbar.LENGTH_LONG).show();
         }
      }
      else {
         // use Activity's ContentResolver to invoke
         // insert on the AccountDataContentProvider
         int updatedRows = getActivity().getContentResolver().update(
            accountUri, contentValues, null, null);

         if (updatedRows > 0) {
            listener.onAddEditCompleted(accountUri);
            Snackbar.make(coordinatorLayout,
               R.string.account_updated, Snackbar.LENGTH_LONG).show();
         }
         else {
            Snackbar.make(coordinatorLayout,
               R.string.account_not_updated, Snackbar.LENGTH_LONG).show();
         }
      }
   }

   // called by LoaderManager to create a Loader
   @Override
   public Loader<Cursor> onCreateLoader(int id, Bundle args) {
      // create an appropriate CursorLoader based on the id argument;
      // only one Loader in this fragment, so the switch is unnecessary
      switch (id) {
         case CONTACT_LOADER:
            return new CursorLoader(getActivity(),
               accountUri, // Uri of account to display
               null, // null projection returns all columns
               null, // null selection returns all rows
               null, // no selection arguments
               null); // sort order
         default:
            return null;
      }
   }

   // called by LoaderManager when loading completes
   @Override
   public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
      // if the account exists in the database, display its data
      if (data != null && data.moveToFirst()) {
         // get the column index for each data item
         int nameIndex = data.getColumnIndex(DatabaseDescription.AccountData.COLUMN_NAME);
         int passwordIndex = data.getColumnIndex(DatabaseDescription.AccountData.COLUMN_PASSWORD);

         // fill EditTexts with the retrieved data
         nameTextInputLayout.getEditText().setText(
            data.getString(nameIndex));

         // decrypt the password to show it in plain text to the user
         String encPwd = data.getString(passwordIndex);
         String plainPwd = encPwd;
         try{
            plainPwd = EncryptionUtil.decryptPassword(encPwd);
         }
         catch (Exception ex){
            Log.e(TAG,"Error while decrypting the password for:" + nameTextInputLayout.getEditText().getText());
         }

         passwordTextInputLayout.getEditText().setText(plainPwd);

         updateSaveButtonFAB();
      }
   }

   // called by LoaderManager when the Loader is being reset
   @Override
   public void onLoaderReset(Loader<Cursor> loader) { }


   /**
    * This implemented method is to listen the click on view
    *
    * @param v
    */
   @Override
   public void onClick(View v) {
      switch (v.getId()) {
         case R.id.textViewLinkSuggestPassword:
            //suggestPasswordTextInputLayout.getEditText().setText(GeneratePassword.newPassword());
            //suggestPasswordTextInputLayout.getEditText().setText(EncryptionUtil.generateRandomPassword(10));
            URL url = createURL();
            if (url != null) {
               GetRandomPasswordTask getRandomPasswordTask = new GetRandomPasswordTask();
               getRandomPasswordTask.execute(url);
            }
            else {
//               Snackbar.make(findViewById(R.id.coordinatorLayout),
//                       R.string.invalid_url, Snackbar.LENGTH_LONG).show();
            }

            break;
      }
   }

   private URL createURL() {
      String apiURL = "https://randommer.io/api/Text/Password?length=12&hasDigits=true&hasUppercase=true&hasSpecial=true";

      try {
         return new URL(apiURL);
      }
      catch (Exception e) {
         Log.e(TAG, "~Error while creating the URL");
      }
      return null; // URL was malformed
   }


   // makes the REST web service call to get weather data and
   // saves the data to a local HTML file
   private class GetRandomPasswordTask
           extends AsyncTask<URL, Void, String> {

      @Override
      protected String doInBackground(URL... params) {
         String apiKey = "34d00eae19e04f6eb2123cdf2a59e1e1";
         String pwd = "<ERROR>";
         HttpURLConnection con = null;
         try {
            con = (HttpURLConnection) params[0].openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("X-Api-Key", apiKey);
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36");
            int response = con.getResponseCode();
            if (response == 200) {
               InputStream is = con.getInputStream();
               Scanner s = new Scanner(is);
               pwd = s.hasNext() ? s.next() : "";
            }
            else {
               // throw new RuntimeException("HttpResponseCode: " + response);
            }
         }
         catch (Exception ex){
            Log.e(TAG, "~Error while getting the connection and random password using REST API");
         }
         finally {
            if(con != null){
               con.disconnect(); // close the HttpURLConnection
            }
         }
         return pwd;
      }

      // process JSON response and update ListView
      @Override
      protected void onPostExecute(String randomPassword) {
         suggestPasswordTextInputLayout.getEditText().setText(randomPassword);
      }
   }

}


/**************************************************************************
 * (C) Copyright 1992-2016 by Deitel & Associates, Inc. and               *
 * Pearson Education, Inc. All Rights Reserved.                           *
 *                                                                        *
 * DISCLAIMER: The authors and publisher of this book have used their     *
 * best efforts in preparing the book. These efforts include the          *
 * development, research, and testing of the theories and programs        *
 * to determine their effectiveness. The authors and publisher make       *
 * no warranty of any kind, expressed or implied, with regard to these    *
 * programs or to the documentation contained in these books. The authors *
 * and publisher shall not be liable in any event for incidental or       *
 * consequential damages in connection with, or arising out of, the       *
 * furnishing, performance, or use of these programs.                     *
 **************************************************************************/
